package com.novel.agentic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.agentic.model.AgentThought;
import com.novel.agentic.model.WritingContext;
import com.novel.agentic.service.orchestrator.AgentOrchestrator;
import com.novel.agentic.service.graph.EntityExtractionService;
import com.novel.agentic.service.CoreStateExtractor;
import com.novel.dto.AIConfigRequest;
import com.novel.domain.entity.Novel;
import com.novel.domain.entity.Chapter;
import com.novel.domain.entity.VolumeChapterOutline;
import com.novel.service.AIWritingService;
import com.novel.service.ChapterService;
import com.novel.service.ChapterSummaryService;
import com.novel.repository.NovelRepository;
import com.novel.repository.VolumeChapterOutlineRepository;
import com.novel.agentic.service.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 代理式章节写作服务
 */
@Service
public class AgenticChapterWriter {

    private static final Logger logger = LoggerFactory.getLogger(AgenticChapterWriter.class);
    private static final boolean ENABLE_ADVANCED_GRAPH_QUERIES = false;


    @Autowired
    private AgentOrchestrator orchestrator;

    @Autowired
    private NovelRepository novelRepository;

    @Autowired
    private ChapterService chapterService;

    @Autowired
    private ChapterSummaryService chapterSummaryService;

    @Autowired
    private AIWritingService aiWritingService;

    @Autowired
    private StructuredMessageBuilder structuredMessageBuilder;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private EntityExtractionService entityExtractionService;

    @Autowired(required = false)
    private StoryContextOptimizer contextOptimizer;

    @Autowired(required = false)
    private com.novel.agentic.service.graph.EntityExtractionRetryService retryService;

    @Autowired(required = false)
    private com.novel.agentic.service.graph.IGraphService graphService;

    @Autowired(required = false)
    private CoreStateExtractor coreStateExtractor;

    @Autowired(required = false)
    private VolumeChapterOutlineRepository outlineRepository;

    @Autowired
    private com.novel.service.AIConfigService aiConfigService;

    @Autowired
    private com.novel.service.VolumeCharacterProfileService volumeCharacterProfileService;



    /**
     * 生成单个章节（简化架构：直接收集上下文 → 直接写作）
     */
    public Chapter generateChapter(
            Long novelId,
            Integer chapterNumber,
            String userAdjustment,
            AIConfigRequest aiConfig,
            String stylePromptFile,
            Long promptTemplateId,
            String outlineContextMode,
            Map<String, String> referenceContents,
            SseEmitter emitter) throws Exception {

        Novel novel = novelRepository.selectById(novelId);
        if (novel == null) {
            throw new IllegalArgumentException("小说不存在: " + novelId);
        }

        // 如果前端没有传递有效的aiConfig，从数据库读取系统配置
        if (aiConfig == null || !aiConfig.isValid()) {
            logger.info("📡 前端未传递有效AI配置，从系统配置读取...");
            try {
                String modelId = aiConfig != null ? aiConfig.getModel() : null;
                aiConfig = aiConfigService.getSystemAIConfig(modelId);
                logger.info("✅ 已从系统配置加载AI配置: provider={}, model={}", 
                    aiConfig.getProvider(), aiConfig.getModel());
            } catch (Exception e) {
                logger.error("❌ 加载系统AI配置失败: {}", e.getMessage());
                throw new RuntimeException("AI配置无效，请先在后台管理页面配置AI服务: " + e.getMessage());
            }
        }
        
        // 创建final变量供lambda表达式使用
        final AIConfigRequest finalAiConfig = aiConfig;

        logger.info("🎬 开始生成章节: {} - 第{}章", novel.getTitle(), chapterNumber);

        // 检查是否重写章节，如果是则清理旧数据
        Chapter existingChapter = chapterService.getChapterByNovelAndNumber(novelId, chapterNumber);
        if (existingChapter != null) {
            logger.warn("⚠️ 检测到重写第{}章，先清理旧的图谱数据和概要...", chapterNumber);
            
            // 检查是否是历史章节
            boolean isHistoricalChapter = checkIfHistoricalChapter(novelId, chapterNumber);
            if (isHistoricalChapter) {
                sendEvent(emitter, "warning", "⚠️ 检测到重写历史章节，图谱数据将保持不变以避免影响后续章节");
                sendEvent(emitter, "warning", "💡 建议：如需完全重写，请从该章节开始依次重写所有后续章节");
            } else {
                sendEvent(emitter, "phase", "🗑️ 清理旧数据中...");
            }
            
            cleanupChapterMetadata(novelId, chapterNumber, emitter);
        }

        // 收集上下文
        sendEvent(emitter, "phase", "📥 收集写作上下文中...");
        String effectiveOutlineContextMode = resolveOutlineContextMode(outlineContextMode, novel);
        WritingContext context = buildDirectWritingContext(
                novelId,
                chapterNumber,
                userAdjustment,
                referenceContents,
                effectiveOutlineContextMode
        );
        if (Novel.OUTLINE_CONTEXT_MODE_FULL.equals(effectiveOutlineContextMode)
                && !Novel.OUTLINE_CONTEXT_MODE_FULL.equals(resolveOutlineContextMode(context.getOutlineContextMode(), null))) {
            sendEvent(emitter, "warning", "当前未提供完整大纲，已自动回退为精简大纲上下文");
        }

        // 图谱健康检查（仅日志）
        checkGraphHealth(novelId, chapterNumber, context);

        // 上下文优化
        if (contextOptimizer != null) {
            try {
                context = contextOptimizer.optimize(context);
                logger.info("✅ 上下文优化完成");
            } catch (Exception e) {
                logger.warn("⚠️ 上下文优化失败，继续使用原始上下文: {}", e.getMessage());
            }
        }

        boolean enableOutlineIntent = true;
        VolumeChapterOutline preGeneratedOutline = null;
        VolumeChapterOutline prevOutline = null;
        VolumeChapterOutline nextOutline = null;
        if (enableOutlineIntent && outlineRepository != null) {
            try {
                preGeneratedOutline = outlineRepository.findByNovelAndGlobalChapter(novelId, chapterNumber);
                if (preGeneratedOutline != null) {
                    logger.info("✅ 找到预生成章纲: 第{}章, 方向={}", chapterNumber, preGeneratedOutline.getDirection());
                    sendEvent(emitter, "outline", "📋 使用预生成章纲");
                    if (chapterNumber != null && chapterNumber > 1) {
                        prevOutline = outlineRepository.findByNovelAndGlobalChapter(novelId, chapterNumber - 1);
                        if (prevOutline != null) {
                            logger.info("✅ 找到前一章预生成章纲: 第{}章, 方向={}", prevOutline.getGlobalChapterNumber(), prevOutline.getDirection());
                        }
                    }
                    nextOutline = outlineRepository.findByNovelAndGlobalChapter(novelId, chapterNumber + 1);
                    if (nextOutline != null) {
                        logger.info("✅ 找到后一章预生成章纲: 第{}章, 方向={}", nextOutline.getGlobalChapterNumber(), nextOutline.getDirection());
                    }
                }
            } catch (Exception e) {
                logger.warn("⚠️ 查询预生成章纲失败: {}", e.getMessage());
            }
        }

        // 推理与意图
        Map<String, Object> plotIntent = null;
        String reasoningPrompt = null;
        String mode = "direct_writing";

        if (enableOutlineIntent && preGeneratedOutline != null) {
            // 有章纲：跳过推理，直接用章纲构建 plotIntent
            sendEvent(emitter, "phase", "📋 使用预生成章纲...");
            plotIntent = convertOutlineToIntent(preGeneratedOutline, prevOutline, nextOutline);
            mode = "outline_direct_writing";
        } else {
            logger.warn("当前章节未查询到细纲，按直接写作继续: novelId={}, chapter={}", novelId, chapterNumber);
            sendEvent(emitter, "warning", "当前章节未查询到细纲，按直接写作继续");
            plotIntent = Collections.emptyMap();
        }

        sendEvent(emitter, "phase", "✍️ AI创作中...");
        Map<String, Object> effectiveIntent = plotIntent != null ? plotIntent : Collections.emptyMap();
        List<Map<String, String>> messages = structuredMessageBuilder.buildMessagesFromIntent(
                novel, context, effectiveIntent, chapterNumber, stylePromptFile, promptTemplateId);


        logger.info("🔍 构建messages后 - 消息总数: {}", messages.size());

        String generationContextSnapshot = serializeGenerationContext(context, messages, mode);

        // 流式生成章节内容
        sendEvent(emitter, "phase", "🤖 AI流式生成中...");
        StringBuilder contentBuilder = new StringBuilder();
        
        aiWritingService.streamGenerateContentWithMessages(
                messages,
                "chapter_writing",
                aiConfig,
                chunk -> {
                    // 累积内容
                    contentBuilder.append(chunk);
                    // 实时发送chunk到前端
                    sendEvent(emitter, "message", chunk);
                }
        );
        
        String generatedContent = contentBuilder.toString();

        if (generatedContent == null || generatedContent.isEmpty()) {
            throw new RuntimeException("AI生成内容为空，请检查AI配置和提示词");
        }

        // 保存章节
        sendEvent(emitter, "phase", "💾 保存中...");
        Chapter chapter = saveChapter(novel, chapterNumber, generatedContent, generationContextSnapshot, aiConfig);

        // 🔐 捕获当前线程的 SecurityContext，以便在异步线程中使用
        SecurityContext securityContext = SecurityContextHolder.getContext();
        Authentication authentication = securityContext.getAuthentication();

        // 异步抽取核心状态并入图
        if (coreStateExtractor != null) {
            CompletableFuture.runAsync(() -> {
                // 🔐 在异步线程中设置 SecurityContext
                SecurityContext asyncContext = SecurityContextHolder.createEmptyContext();
                asyncContext.setAuthentication(authentication);
                SecurityContextHolder.setContext(asyncContext);
                try {
                    sendEvent(emitter, "phase", "🔍 抽取核心状态中...");
                    coreStateExtractor.extractAndSaveCoreState(
                            novel.getId(),
                            chapterNumber,
                            generatedContent,
                            chapter.getTitle(),
                            finalAiConfig
                    );
                    sendEvent(emitter, "extraction", "✅ 核心状态抽取完成");
                } catch (Exception e) {
                    logger.error("核心状态抽取失败（不阻塞章节保存）", e);
                    sendEvent(emitter, "extraction", "⚠️ 核心状态抽取失败: " + e.getMessage());
                } finally {
                    // 🔐 清理异步线程的 SecurityContext
                    SecurityContextHolder.clearContext();
                }
            });
        }
        // 异步抽取结构化实体并入图
        if (entityExtractionService != null) {
            CompletableFuture.runAsync(() -> {
                // 🔐 在异步线程中设置 SecurityContext
                SecurityContext asyncContext = SecurityContextHolder.createEmptyContext();
                asyncContext.setAuthentication(authentication);
                SecurityContextHolder.setContext(asyncContext);
                try {
                    sendEvent(emitter, "phase", "🔎 抽取结构化实体中...");
                    entityExtractionService.extractAndSave(
                            novel.getId(),
                            chapterNumber,
                            chapter.getTitle(),
                            generatedContent,
                            finalAiConfig
                    );
                    sendEvent(emitter, "extraction", "✅ 实体抽取完成");
                } catch (Exception e) {
                    logger.error("实体抽取失败（不阻塞章节保存）", e);
                    sendEvent(emitter, "extraction", "⚠️ 实体抽取失败: " + e.getMessage());
                } finally {
                    // 🔐 清理异步线程的 SecurityContext
                    SecurityContextHolder.clearContext();
                }
            });
        }


        sendEvent(emitter, "complete", "✅ 生成完成！共 " + generatedContent.length() + " 字");
        logger.info("✅ 章节生成完成: 第{}章, 字数{}", chapterNumber, generatedContent.length());

        return chapter;
    }

    /**
     * 批量生成多个章节
     */
    public List<Chapter> generateMultipleChapters(
            Long novelId,
            Integer startChapter,
            Integer count,
            AIConfigRequest aiConfig,
            String stylePromptFile,
            Long promptTemplateId,
            String outlineContextMode,
            Map<String, String> referenceContents,
            SseEmitter emitter) throws Exception {

        logger.info("📚 开始批量生成: novelId={}, 起始章节={}, 数量={}", novelId, startChapter, count);

        // 如果前端没有传递有效的aiConfig，从数据库读取系统配置
        if (aiConfig == null || !aiConfig.isValid()) {
            logger.info("📡 前端未传递有效AI配置，从系统配置读取...");
            try {
                String modelId = aiConfig != null ? aiConfig.getModel() : null;
                aiConfig = aiConfigService.getSystemAIConfig(modelId);
                logger.info("✅ 已从系统配置加载AI配置: provider={}, model={}", 
                    aiConfig.getProvider(), aiConfig.getModel());
            } catch (Exception e) {
                logger.error("❌ 加载系统AI配置失败: {}", e.getMessage());
                throw new RuntimeException("AI配置无效，请先在后台管理页面配置AI服务: " + e.getMessage());
            }
        }

        List<Chapter> chapters = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Integer currentChapter = startChapter + i;

            sendEvent(emitter, "chapter_start", "开始生成第 " + currentChapter + " 章 (" + (i + 1) + "/" + count + ")");

            try {
                Chapter chapter = generateChapter(
                        novelId,
                        currentChapter,
                        null,
                        aiConfig,
                        stylePromptFile,
                        promptTemplateId,
                        outlineContextMode,
                        referenceContents,
                        emitter
                );
                chapters.add(chapter);

                // 🔧 验证章节是否正确保存
                Chapter verifyChapter = chapterService.getChapterByNovelAndNumber(novelId, currentChapter);
                if (verifyChapter == null || verifyChapter.getContent() == null || verifyChapter.getContent().isEmpty()) {
                    logger.error("⚠️ 章节{}保存验证失败，内容为空或未保存", currentChapter);
                } else {
                    logger.info("✅ 章节{}保存验证通过，字数: {}", currentChapter, verifyChapter.getContent().length());
                }

                // 章节间短暂休息，避免API限流
                if (i < count - 1) {
                    Thread.sleep(2000);
                }

            } catch (Exception e) {
                logger.error("生成第{}章失败", currentChapter, e);
                sendEvent(emitter, "error", "第 " + currentChapter + " 章生成失败: " + e.getMessage());

                // 决定是否继续
                if (e.getMessage().contains("API") || e.getMessage().contains("limit")) {
                    logger.error("遇到API限制，停止批量生成");
                    break;
                }
            }
        }

        sendEvent(emitter, "batch_complete", "批量生成完成！共生成 " + chapters.size() + " 章");
        logger.info("✅ 批量生成完成: 成功{}章", chapters.size());

        return chapters;
    }

    /**
     * 直接构建写作上下文：最近1章全文 + 前30章概要 + 图谱数据 + 大纲蓝图
     *
     * 对外公开，便于重写/编辑等场景复用同一套上下文来源。
     */
    public WritingContext buildDirectWritingContext(Long novelId, Integer chapterNumber, String userAdjustment, Map<String, String> referenceContents) {
        return buildDirectWritingContext(
                novelId,
                chapterNumber,
                userAdjustment,
                referenceContents,
                Novel.OUTLINE_CONTEXT_MODE_SIMPLIFIED
        );
    }

    public WritingContext buildDirectWritingContext(
            Long novelId,
            Integer chapterNumber,
            String userAdjustment,
            Map<String, String> referenceContents,
            String outlineContextMode) {
        WritingContext.WritingContextBuilder contextBuilder = WritingContext.builder();
        contextBuilder.userAdjustment(userAdjustment);
        contextBuilder.outlineContextMode(resolveOutlineContextMode(outlineContextMode, null));
        boolean includeHistoricalEventsInDirectWriting = false;
        if (referenceContents != null && !referenceContents.isEmpty()) {
            contextBuilder.referenceContents(referenceContents);
        }

        try {
            // 1. 获取核心设定（替代大纲）
            Map<String, Object> outlineArgs = new HashMap<>();
            outlineArgs.put("novelId", novelId);
            outlineArgs.put("mode", resolveOutlineContextMode(outlineContextMode, null));
            Object outlineResult = toolRegistry.executeTool("getOutline", outlineArgs);
            if (outlineResult instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> outlineMap = (Map<String, Object>) outlineResult;
                Object core = outlineMap.get("coreSettings");
                if (core instanceof String && StringUtils.hasText((String) core)) {
                    contextBuilder.coreSettings((String) core);
                }
                Object fullOutline = outlineMap.get("fullOutline");
                if (fullOutline instanceof String && StringUtils.hasText((String) fullOutline)) {
                    contextBuilder.outline((String) fullOutline);
                }
                Object resolvedMode = outlineMap.get("resolvedMode");
                if (resolvedMode instanceof String && StringUtils.hasText((String) resolvedMode)) {
                    contextBuilder.outlineContextMode(resolveOutlineContextMode((String) resolvedMode, null));
                }
            } else if (outlineResult instanceof String) {
                contextBuilder.coreSettings((String) outlineResult);
            }

            // 2. 获取卷蓝图
            Map<String, Object> blueprintArgs = new HashMap<>();
            blueprintArgs.put("novelId", novelId);
            blueprintArgs.put("chapterNumber", chapterNumber);
            Object blueprintResult = toolRegistry.executeTool("getVolumeBlueprint", blueprintArgs);
            if (blueprintResult instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> blueprintMap = (Map<String, Object>) blueprintResult;
                contextBuilder.volumeBlueprint(blueprintMap);

                Long volumeId = getLongValue(blueprintMap.get("volumeId"));
                if (volumeId != null) {
                    List<Map<String, Object>> volumeProfiles = loadVolumeCharacterProfiles(volumeId);
                    if (!volumeProfiles.isEmpty()) {
                        contextBuilder.volumeCharacterProfiles(volumeProfiles);
                        logger.info("✅ 已加载{}个本卷人设卡: volumeId={}", volumeProfiles.size(), volumeId);
                    }
                }

                // 详细日志：检查卷蓝图内容
                Object blueprint = blueprintMap.get("blueprint");
                logger.info("✅ 已加载卷蓝图: volumeId={}, volumeTitle={}, blueprint={}",
                        blueprintMap.get("volumeId"),
                        blueprintMap.get("volumeTitle"),
                        blueprint != null ? (blueprint.toString().length() > 100 ?
                                blueprint.toString().substring(0, 100) + "..." : blueprint.toString()) : "NULL");

                if (blueprint == null || "暂无蓝图".equals(blueprint)) {
                    logger.warn("⚠️ 卷蓝图为空或未生成！请先为卷{}生成蓝图", blueprintMap.get("volumeId"));
                }
            }

            // 3. 获取当前章节细纲
            if (outlineRepository != null && chapterNumber != null && chapterNumber > 0) {
                try {
                    VolumeChapterOutline currentOutline = outlineRepository.findByNovelAndGlobalChapter(novelId, chapterNumber);
                    if (currentOutline != null) {
                        String currentChapterOutline = buildCurrentChapterOutlineText(currentOutline);
                        if (StringUtils.hasText(currentChapterOutline)) {
                            contextBuilder.currentChapterOutline(currentChapterOutline);
                            logger.info("已加载当前章节细纲: chapter={}", chapterNumber);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("加载当前章节细纲失败: {}", e.getMessage());
                }
            }

            // 4. 获取最近1章完整内容
            if (chapterNumber > 1) {
                List<Chapter> recentChapters = chapterService.getRecentChapters(novelId, chapterNumber, 1);
                if (recentChapters != null && !recentChapters.isEmpty()) {
                    List<Map<String, Object>> recentFullChapters = new ArrayList<>();
                    for (Chapter chapter : recentChapters) {
                        Map<String, Object> chapterData = new HashMap<>();
                        chapterData.put("chapterNumber", chapter.getChapterNumber());
                        chapterData.put("title", chapter.getTitle());
                        chapterData.put("content", chapter.getContent());
                        recentFullChapters.add(chapterData);
                        logger.info("✅ 已加载前一章完整内容: 第{}章 ({}字)",
                                chapter.getChapterNumber(),
                                chapter.getContent() != null ? chapter.getContent().length() : 0);
                    }
                    contextBuilder.recentFullChapters(recentFullChapters);
                }
            }

            // 4. 获取前30章概要
            if (chapterNumber > 1) {
                List<Map<String, Object>> summaries = chapterSummaryService.getRecentSummaries(novelId, chapterNumber - 1, 30);
                if (summaries != null && !summaries.isEmpty()) {
                    contextBuilder.recentSummaries(summaries);
                    logger.info("✅ 已加载最近{}章概要", summaries.size());
                }
            }

            // 5. 获取图谱数据（角色档案）
            if (chapterNumber > 1) {
                Map<String, Object> characterArgs = new HashMap<>();
                characterArgs.put("novelId", novelId);
                characterArgs.put("limit", 200);
                Object characterResult = toolRegistry.executeTool("getCharacterProfiles", characterArgs);
                if (characterResult instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> characters = (List<Map<String, Object>>) characterResult;
                    contextBuilder.characterProfiles(characters);
                    logger.info("✅ 已加载{}个角色档案", characters.size());
                }
            }

            // 6. 获取图谱数据（相关事件）
            if (includeHistoricalEventsInDirectWriting && chapterNumber > 1 && graphService != null) {
                try {
                    List<com.novel.agentic.model.GraphEntity> events = graphService.getRelevantEvents(novelId, chapterNumber, 10);
                    if (events != null && !events.isEmpty()) {
                        contextBuilder.relevantEvents(events);
                        logger.info("✅ 已加载{}个历史事件", events.size());
                    }
                } catch (Exception e) {
                    logger.warn("⚠️ 获取历史事件失败: {}", e.getMessage());
                }
            }

            // 7. 获取未解决的伏笔
            if (chapterNumber > 1 && graphService != null) {
                try {
                    List<com.novel.agentic.model.GraphEntity> foreshadows = graphService.getUnresolvedForeshadows(novelId, chapterNumber, 10);
                    if (foreshadows != null && !foreshadows.isEmpty()) {
                        contextBuilder.unresolvedForeshadows(foreshadows);
                        logger.info("✅ 已加载{}个待回收伏笔", foreshadows.size());
                    }
                } catch (Exception e) {
                    logger.warn("⚠️ 获取伏笔失败: {}", e.getMessage());
                }
            }

            if (ENABLE_ADVANCED_GRAPH_QUERIES) {
                // 8-11. 高级图谱查询（情节线/冲突弧线/角色弧线/叙事节奏）已暂时停用，待实体落地与关系完善后再启用。
                // 目的：避免空查询拉低上下文信号密度，聚焦“事件 + 伏笔 + 账本”。
            }


            // 12. 核心记忆账本（强约束）：角色状态 / 关系 / 未决任务
            java.util.List<java.util.Map<String, Object>> __charStates = null;
            java.util.List<java.util.Map<String, Object>> __relationships = null;
            java.util.List<java.util.Map<String, Object>> __openQuests = null;
            if (graphService != null) {
                try {
                    __charStates = graphService.getCharacterStates(novelId, 200);
                    if (__charStates != null && !__charStates.isEmpty()) {
                        contextBuilder.characterStates(__charStates);
                        logger.info("✅ 已加载{}个角色状态", __charStates.size());
                    }
                } catch (Exception e) {
                    logger.warn("⚠️ 获取角色状态失败: {}", e.getMessage());
                }
                try {
                    __relationships = graphService.getTopRelationships(novelId, 200);
                    if (__relationships != null && !__relationships.isEmpty()) {
                        contextBuilder.relationshipStates(__relationships);
                        logger.info("✅ 已加载{}条关系状态", __relationships.size());
                    }
                } catch (Exception e) {
                    logger.warn("⚠️ 获取关系状态失败: {}", e.getMessage());
                }
                // 🔕 注释掉未决任务：剧情按章纲发展，未决任务容易干扰AI写作
                // try {
                //     __openQuests = graphService.getOpenQuests(novelId, chapterNumber);
                //     if (__openQuests != null && !__openQuests.isEmpty()) {
                //         contextBuilder.openQuests(__openQuests);
                //         logger.info("✅ 已加载{}个未决任务", __openQuests.size());
                //     }
                // } catch (Exception e) {
                //     logger.warn("⚠️ 获取未决任务失败: {}", e.getMessage());
                // }
            }

            // 13. 生成轻量章合同（基于章纲，不再依赖未决任务）
            // 🔕 注释掉基于未决任务的章合同生成，改为依赖章纲
            // try {
            //     java.util.Map<String, Object> plan = new java.util.HashMap<>();
            //     plan.put("chapterNumber", chapterNumber);
            //     plan.put("estimatedWords", 2500);
            //
            //     // 基于未决任务推导本章意图
            //     if (__openQuests != null && !__openQuests.isEmpty()) {
            //         StringBuilder purpose = new StringBuilder("推进未决任务：");
            //         int limit = Math.min(2, __openQuests.size());
            //         for (int i = 0; i < limit; i++) {
            //             java.util.Map<String, Object> q = __openQuests.get(i);
            //             Object id = q.get("id");
            //             Object desc = q.get("description");
            //             purpose.append("[").append(id != null ? id : (i + 1)).append("] ");
            //             if (desc != null) {
            //                 String d = desc.toString();
            //                 purpose.append(d.length() > 24 ? d.substring(0, 24) + "..." : d);
            //             }
            //             if (i < limit - 1) purpose.append("；");
            //         }
            //         plan.put("purpose", purpose.toString());
            //         plan.put("primaryFocus", "QUEST_PROGRESS");
            //     }
            //
            //     contextBuilder.chapterPlan(plan);
            // } catch (Exception ignore) {
            //     // 忽略章合同推导失败
            // }

        } catch (Exception e) {
            logger.error("❌ 构建直接写作上下文失败", e);
        }
        return contextBuilder.build();
    }

    private String resolveOutlineContextMode(String outlineContextMode, Novel novel) {
        if (Novel.OUTLINE_CONTEXT_MODE_FULL.equalsIgnoreCase(outlineContextMode)) {
            return Novel.OUTLINE_CONTEXT_MODE_FULL;
        }
        if (Novel.OUTLINE_CONTEXT_MODE_SIMPLIFIED.equalsIgnoreCase(outlineContextMode)) {
            return Novel.OUTLINE_CONTEXT_MODE_SIMPLIFIED;
        }
        if (novel != null && Novel.OUTLINE_CONTEXT_MODE_FULL.equalsIgnoreCase(novel.getOutlineContextMode())) {
            return Novel.OUTLINE_CONTEXT_MODE_FULL;
        }
        return Novel.OUTLINE_CONTEXT_MODE_SIMPLIFIED;
    }

    private String buildCurrentChapterOutlineText(VolumeChapterOutline outline) {
        if (outline == null) {
            return null;
        }

        String direction = resolveOutlineDirection(outline);
        String foreshadowDetail = buildOutlineStructuredFieldText(outline.getForeshadowDetail());
        String antagonism = buildOutlineStructuredFieldText(outline.getAntagonism());

        StringBuilder sb = new StringBuilder();
        boolean hasBody = false;

        sb.append("【当前章节细纲（优先遵守）】\n");
        if (outline.getGlobalChapterNumber() != null) {
            sb.append("章节：第").append(outline.getGlobalChapterNumber()).append("章\n");
        } else if (outline.getChapterInVolume() != null) {
            sb.append("卷内章节：第").append(outline.getChapterInVolume()).append("章\n");
        }

        if (StringUtils.hasText(direction)) {
            sb.append("核心细纲：\n").append(direction.trim()).append("\n");
            hasBody = true;
        }
        if (StringUtils.hasText(outline.getEmotionalTone())) {
            sb.append("情感基调：").append(outline.getEmotionalTone().trim()).append("\n");
            hasBody = true;
        }
        if (StringUtils.hasText(outline.getSubplot())) {
            sb.append("支线/人物刻画：").append(outline.getSubplot().trim()).append("\n");
            hasBody = true;
        }
        if (StringUtils.hasText(outline.getForeshadowAction())
                && !"NONE".equalsIgnoreCase(outline.getForeshadowAction().trim())) {
            sb.append("伏笔操作：").append(outline.getForeshadowAction().trim()).append("\n");
            hasBody = true;
        }
        if (StringUtils.hasText(foreshadowDetail)) {
            sb.append("伏笔细节：\n").append(foreshadowDetail).append("\n");
            hasBody = true;
        }
        if (StringUtils.hasText(antagonism)) {
            sb.append("对手/阻力与赌注：\n").append(antagonism).append("\n");
            hasBody = true;
        }

        return hasBody ? sb.toString().trim() : null;
    }

    private List<Map<String, Object>> loadVolumeCharacterProfiles(Long volumeId) {
        if (volumeId == null) {
            return Collections.emptyList();
        }
        try {
            List<com.novel.domain.entity.VolumeCharacterProfile> profiles =
                volumeCharacterProfileService.getByVolumeId(volumeId);
            if (profiles == null || profiles.isEmpty()) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (com.novel.domain.entity.VolumeCharacterProfile profile : profiles) {
                if (profile == null) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("characterName", profile.getCharacterName());
                item.put("currentPersona", profile.getCurrentPersona());
                item.put("coreMotivation", profile.getCoreMotivation());
                item.put("stanceRelationships", profile.getStanceRelationships());
                item.put("unfinishedGoals", profile.getUnfinishedGoals());
                item.put("behaviorBottomLine", profile.getBehaviorBottomLine());
                result.add(item);
            }
            return result;
        } catch (Exception e) {
            logger.warn("⚠️ 加载本卷人设失败: volumeId={}, err={}", volumeId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private Long getLongValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    /**
     * 保存章节
     */
    private Chapter saveChapter(Novel novel, Integer chapterNumber, String content, String generationContext, AIConfigRequest aiConfig) {
        Chapter existing = chapterService.getChapterByNovelAndNumber(novel.getId(), chapterNumber);
        Chapter persisted;
        if (existing == null) {
            Chapter chapter = new Chapter();
            chapter.setNovelId(novel.getId());
            chapter.setChapterNumber(chapterNumber);
            chapter.setTitle("第" + chapterNumber + "章");
            chapter.setContent(content);
            chapter.setGenerationContext(generationContext);
            persisted = chapterService.createChapter(chapter);
        } else {
            Chapter update = new Chapter();
            update.setTitle(existing.getTitle() != null ? existing.getTitle() : "第" + chapterNumber + "章");
            update.setContent(content);
            update.setGenerationContext(generationContext);
            persisted = chapterService.updateChapterInternal(existing.getId(), update);
        }

        if (persisted != null) {
            try {
                chapterSummaryService.generateOrUpdateSummary(persisted, aiConfig);
                logger.info("✅ 章节概括已生成: novelId={}, chapter={}", novel.getId(), chapterNumber);
            } catch (Exception e) {
                logger.error("❌ 章节概括生成失败: novelId={}, chapter={}, 错误: {}",
                        novel.getId(), chapterNumber, e.getMessage(), e);
                // 概括生成失败不应阻止章节保存，但必须记录错误供后续排查
            }
        }

        return persisted;
    }

    /**
     * 发送决策过程
     */
    private void sendDecisionProcess(SseEmitter emitter, List<AgentThought> thoughts) {
        if (thoughts == null || thoughts.isEmpty()) {
            return;
        }

        StringBuilder process = new StringBuilder();
        process.append("\n【AI决策过程】\n");
        for (AgentThought thought : thoughts) {
            process.append("Step ").append(thought.getStepNumber()).append(": ")
                    .append(thought.getAction()).append("\n");
            process.append("  思考: ").append(thought.getReasoning()).append("\n");
        }

        sendEvent(emitter, "decision", process.toString());
    }

    /**
     * 发送SSE事件
     */
    private void sendEvent(SseEmitter emitter, String eventType, String data) {
        if (emitter == null) {
            return;
        }
        try {
            String payload = data == null ? "" : data;
            
            // 🔧 修复 SSE 格式问题：
            // 对于 message 事件（流式输出），移除换行符避免破坏 SSE 协议
            // 换行由前端的 applyRealtimeLineBreaks() 函数处理
            if ("message".equals(eventType)) {
                payload = payload.replace("\r\n", "\n").replace("\r", "\n");
            } else {
                // 其他事件（phase、outline等）保留换行符
                payload = payload.replace("\r\n", "\n").replace("\r", "\n");
            }
            
            SseEmitter.SseEventBuilder builder = SseEmitter.event();
            if (StringUtils.hasText(eventType)) {
                builder.name(eventType);
            }
            if ("message".equals(eventType)) {
                builder.data(objectMapper.writeValueAsString(Collections.singletonMap("chunk", payload)), MediaType.APPLICATION_JSON);
            } else {
                for (String line : payload.split("\n", -1)) {
                    builder.data(line, MediaType.TEXT_PLAIN);
                }
            }
            emitter.send(builder);
        } catch (IllegalStateException ex) {
        } catch (IOException ex) {
            logger.error("发送SSE事件失败: {}", eventType, ex);
        }
    }

    /**
     * 序列化写作上下文（仅保存封装后的 messages）
     */
    private String serializeGenerationContext(WritingContext context, List<Map<String, String>> messages, String mode) {
        // 只保存 messages，避免在DB存储任何其它上下文内容
        try {
            if (messages == null) {
                return "[]";
            }
            return objectMapper.writeValueAsString(messages);
        } catch (Exception e) {
            logger.warn("无法序列化messages，使用简化版本", e);
            try {
                // 简化为仅保留 role 与 content
                List<Map<String, String>> minimal = new ArrayList<>();
                if (messages != null) {
                    for (Map<String, String> m : messages) {
                        if (m == null) continue;
                        Map<String, String> mm = new HashMap<>();
                        mm.put("role", m.get("role"));
                        mm.put("content", m.get("content"));
                        minimal.add(mm);
                    }
                }
                return objectMapper.writeValueAsString(minimal);
            } catch (Exception fallback) {
                logger.error("messages序列化完全失败", fallback);
                return "[]";
            }
        }
    }

    private void checkGraphHealth(Long novelId, Integer chapterNumber, WritingContext context) {
        int totalGraphEntities = 0;

        if (context.getRelevantEvents() != null) {
            totalGraphEntities += context.getRelevantEvents().size();
        }
        if (context.getUnresolvedForeshadows() != null) {
            totalGraphEntities += context.getUnresolvedForeshadows().size();
        }
        if (context.getConflictArcs() != null) {
            totalGraphEntities += context.getConflictArcs().size();
        }
        if (context.getCharacterArcs() != null) {
            totalGraphEntities += context.getCharacterArcs().size();
        }
        if (context.getPlotlineStatus() != null) {
            totalGraphEntities += context.getPlotlineStatus().size();
        }

        if (chapterNumber > 5 && totalGraphEntities == 0) {
            logger.warn("⚠️ 图谱健康检查：第{}章未检索到任何图谱数据。可能原因：1) 实体抽取失败 2) Neo4j未启动或连接失败 3) 数据尚未入图", chapterNumber);
            logger.warn("   建议：检查日志中是否有实体抽取错误，或运行 docker-compose up neo4j 启动图数据库");
        } else if (totalGraphEntities > 0) {
            logger.info("✅ 图谱健康检查：第{}章成功加载{}个图谱实体", chapterNumber, totalGraphEntities);
            if (context.getRelevantEvents() != null && !context.getRelevantEvents().isEmpty()) {
                long eventsWithCausal = context.getRelevantEvents().stream()
                        .filter(e -> e.getProperties().containsKey("causalFrom") || e.getProperties().containsKey("causalTo"))
                        .count();
                logger.info("   - 历史事件: {} 个（含因果关系: {} 个）",
                        context.getRelevantEvents().size(), eventsWithCausal);
            }
            if (context.getUnresolvedForeshadows() != null && !context.getUnresolvedForeshadows().isEmpty()) {
                logger.info("   - 待回收伏笔: {} 个", context.getUnresolvedForeshadows().size());
            }
            if (context.getConflictArcs() != null && !context.getConflictArcs().isEmpty()) {
                logger.info("   - 冲突弧线: {} 个", context.getConflictArcs().size());
            }
        } else if (chapterNumber <= 5) {
            logger.info("ℹ️ 图谱健康检查：第{}章，图谱数据为空（前5章正常，因为实体需要累积）", chapterNumber);
        }
    }

    /**
     * 🆕 填充最近章节内容和概要到上下文
     */
    private void enrichContextWithRecentChapters(Long novelId, Integer chapterNumber, WritingContext context) {
        try {
            // 1. 查询最近3章完整内容（不包括当前章）
            // 注意：写第N章时，应该参考第N-3, N-2, N-1章
            List<Chapter> recentChapters = chapterService.getRecentChapters(novelId, chapterNumber, 3);
            logger.info("🔍 查询最近章节: novelId={}, currentChapter={}, limit=3", novelId, chapterNumber);

            if (recentChapters != null && !recentChapters.isEmpty()) {
                // recentChapters 是降序排列（7,6,5），需要反转为升序（5,6,7）
                java.util.Collections.reverse(recentChapters);

                List<Map<String, Object>> recentFullChapters = new ArrayList<>();
                for (Chapter chapter : recentChapters) {
                    Map<String, Object> chapterData = new HashMap<>();
                    chapterData.put("chapterNumber", chapter.getChapterNumber());
                    chapterData.put("title", chapter.getTitle());
                    chapterData.put("content", chapter.getContent());
                    recentFullChapters.add(chapterData);
                    logger.info("  📖 加载第{}章: {} ({}字)",
                            chapter.getChapterNumber(), chapter.getTitle(),
                            chapter.getContent() != null ? chapter.getContent().length() : 0);
                }
                context.setRecentFullChapters(recentFullChapters);
                logger.info("✅ 已加载最近{}章完整内容到上下文（章节范围：{}-{}）",
                        recentFullChapters.size(),
                        recentChapters.get(0).getChapterNumber(),
                        recentChapters.get(recentChapters.size() - 1).getChapterNumber());
            } else {
                logger.warn("⚠️ 未查询到任何最近章节内容");
            }

            // 2. 查询最近10章概要（不包括当前章和已包含的完整章节）
            List<Map<String, Object>> summaries = chapterSummaryService.getRecentSummaries(novelId, chapterNumber - 1, 10);
            if (summaries != null && !summaries.isEmpty()) {
                context.setRecentSummaries(summaries);
                logger.info("✅ 已加载最近{}章概要到上下文", summaries.size());
            }
        } catch (Exception e) {
            logger.warn("⚠️ 加载最近章节内容失败（将使用空上下文）: {}", e.getMessage());
        }
    }

    /**
     * 检查是否是历史章节（后面还有更新的章节）
     */
    private boolean checkIfHistoricalChapter(Long novelId, Integer chapterNumber) {
        try {
            // 查询该小说是否有更大章节号的章节
            Chapter laterChapter = chapterService.getChapterByNovelAndNumber(novelId, chapterNumber + 1);
            if (laterChapter != null) {
                logger.info("📊 检测到历史章节: 当前章节={}, 存在后续章节", chapterNumber);
                return true;
            }
            
            // 也可以通过查询所有章节来确认
            List<Chapter> allChapters = chapterService.getChaptersByNovelId(novelId);
            if (allChapters != null) {
                for (Chapter chapter : allChapters) {
                    if (chapter.getChapterNumber() != null && chapter.getChapterNumber() > chapterNumber) {
                        logger.info("📊 检测到历史章节: 当前章节={}, 最大章节>={}", chapterNumber, chapter.getChapterNumber());
                        return true;
                    }
                }
            }
            
            return false;
        } catch (Exception e) {
            logger.warn("⚠️ 检查历史章节失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 🆕 清理章节的图谱数据和概要（用于重写章节时）
     */
    private void cleanupChapterMetadata(Long novelId, Integer chapterNumber, SseEmitter emitter) {
        try {
            // 1. 删除图谱中该章节的所有实体和关系
            if (graphService != null) {
                logger.info("🗑️ 删除第{}章的图谱数据...", chapterNumber);
                graphService.deleteChapterEntities(novelId, chapterNumber);
                logger.info("✅ 图谱数据清理完成");
            } else {
                logger.warn("⚠️ GraphService未注入，跳过图谱数据清理");
            }

            // 2. 删除该章节的概要
            if (chapterSummaryService != null) {
                logger.info("🗑️ 删除第{}章的概要...", chapterNumber);
                chapterSummaryService.deleteChapterSummary(novelId, chapterNumber);
                logger.info("✅ 概要数据清理完成");
            } else {
                logger.warn("⚠️ ChapterSummaryService未注入，跳过概要清理");
            }
        } catch (Exception e) {
            logger.error("❌ 清理章节元数据失败（继续生成）: {}", e.getMessage(), e);
            // 不抛出异常，允许继续生成
        }
    }

    /**
     * 将预生成章纲转换为 plotIntent 格式
     * 简化版：只使用 direction（本章剧情方向）和伏笔字段
     */
    private Map<String, Object> convertOutlineToIntent(VolumeChapterOutline outline, VolumeChapterOutline prevOutline, VolumeChapterOutline nextOutline) {
        Map<String, Object> intent = new HashMap<>();
        
        // direction 作为本章剧情方向（包含关键剧情点，不再使用数组格式）
        intent.put("direction", resolveOutlineDirection(outline));

        // 伏笔相关字段
        if (outline.getForeshadowDetail() != null) {
            try {
                Map<String, Object> detail = objectMapper.readValue(outline.getForeshadowDetail(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>(){});
                intent.put("foreshadowsToResolve", detail.get("anchorsUsed"));
                intent.put("foreshadowAction", outline.getForeshadowAction());
                intent.put("foreshadowDetail", detail);
            } catch (Exception e) {
                logger.warn("解析foreshadowDetail失败: {}", e.getMessage());
            }
        } else if (outline.getForeshadowAction() != null) {
            intent.put("foreshadowAction", outline.getForeshadowAction());
        }

        // 后续章节剧情方向提示（用于避免剧情冲突）
        if (outlineRepository != null) {
            try {
                List<VolumeChapterOutline> futureOutlines = new ArrayList<>();
                Long futureNovelId = outline.getNovelId();
                Integer baseChapter = outline.getGlobalChapterNumber();

                if (futureNovelId != null && baseChapter != null) {
                    for (int i = 1; i <= 10; i++) {
                        VolumeChapterOutline future = outlineRepository.findByNovelAndGlobalChapter(futureNovelId, baseChapter + i);
                        if (future == null) {
                            break;
                        }
                        futureOutlines.add(future);
                    }
                } else if (nextOutline != null) {
                    futureOutlines.add(nextOutline);
                }

                if (!futureOutlines.isEmpty()) {
                    StringBuilder hint = new StringBuilder();
                    hint.append("\n\n【后续章节剧情方向(主要是为了让你了解后续剧情节点发展 别提前写 当前写的逻辑也不能后面的有冲突 注意逻辑关系)】 ：\n");
                    for (VolumeChapterOutline fo : futureOutlines) {
                        Integer g = fo.getGlobalChapterNumber();
                        Integer civ = fo.getChapterInVolume();
                        if (g != null) {
                            hint.append("第").append(g).append("章：");
                        } else if (civ != null) {
                            hint.append("卷内第").append(civ).append("章：");
                        } else {
                            hint.append("未来章节：");
                        }

                        String futureDirection = resolveOutlineDirection(fo);
                        if (StringUtils.hasText(futureDirection)) {
                            hint.append(futureDirection).append("\n");
                        } else {
                            hint.append("（暂无剧情方向）\n");
                        }
                    }
                    hint.append("\n注意:本章剧情需要在不抢先写完上述剧情方向全部结果的前提下，自然衔接并为这些发展做好铺垫。");
                    intent.put("adjacentOutlineHint", hint.toString());
                }
            } catch (Exception e) {
                logger.warn("构造后续章节剧情方向提示失败: {}", e.getMessage());
            }
        }

        return intent;
    }

    private String resolveOutlineDirection(VolumeChapterOutline outline) {
        if (outline == null) {
            return null;
        }
        String fromKeyPlot = extractKeyPlotPointsText(outline.getKeyPlotPoints());
        if (StringUtils.hasText(fromKeyPlot)) {
            return fromKeyPlot;
        }
        return outline.getDirection();
    }

    private String buildOutlineStructuredFieldText(String raw) {
        return extractKeyPlotPointsText(raw);
    }

    private String extractKeyPlotPointsText(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            Object parsed = objectMapper.readValue(trimmed, Object.class);
            if (parsed instanceof List) {
                StringBuilder sb = new StringBuilder();
                for (Object item : (List<?>) parsed) {
                    if (item == null) {
                        continue;
                    }
                    String line = item.toString().trim();
                    if (line.isEmpty()) {
                        continue;
                    }
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(line);
                }
                return sb.length() > 0 ? sb.toString() : null;
            }
            if (parsed instanceof String) {
                String line = ((String) parsed).trim();
                return line.isEmpty() ? null : line;
            }
            if (parsed instanceof Map) {
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
            }
        } catch (Exception e) {
            // ignore and fall back to raw text
        }
        return trimmed;
    }


}
