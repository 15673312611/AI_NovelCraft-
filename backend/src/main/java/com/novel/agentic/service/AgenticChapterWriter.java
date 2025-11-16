package com.novel.agentic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.agentic.model.AgentThought;
import com.novel.agentic.model.WritingContext;
import com.novel.agentic.service.orchestrator.AgentOrchestrator;
import com.novel.agentic.service.graph.EntityExtractionService;
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

    @Autowired
    private PlotReasoningService plotReasoningService;

    @Autowired(required = false)
    private VolumeChapterOutlineRepository outlineRepository;



    /**
     * 生成单个章节（简化架构：直接收集上下文 → 直接写作）
     */
    public Chapter generateChapter(
            Long novelId,
            Integer chapterNumber,
            String userAdjustment,
            AIConfigRequest aiConfig,
            String stylePromptFile,
            Map<String, String> referenceContents,
            SseEmitter emitter) throws Exception {

        Novel novel = novelRepository.selectById(novelId);
        if (novel == null) {
            throw new IllegalArgumentException("小说不存在: " + novelId);
        }

        logger.info("🎬 开始生成章节: {} - 第{}章", novel.getTitle(), chapterNumber);

        // 检查是否重写章节，如果是则清理旧数据
        Chapter existingChapter = chapterService.getChapterByNovelAndNumber(novelId, chapterNumber);
        if (existingChapter != null) {
            logger.warn("⚠️ 检测到重写第{}章，先清理旧的图谱数据和概要...", chapterNumber);
            sendEvent(emitter, "phase", "🗑️ 清理旧数据中...");
            cleanupChapterMetadata(novelId, chapterNumber);
        }

        // 收集上下文
        sendEvent(emitter, "phase", "📥 收集写作上下文中...");
        WritingContext context = buildDirectWritingContext(novelId, chapterNumber, userAdjustment, referenceContents);

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

        // 优先查询预生成章纲
        VolumeChapterOutline preGeneratedOutline = null;
        if (outlineRepository != null) {
            try {
                preGeneratedOutline = outlineRepository.findByNovelAndGlobalChapter(novelId, chapterNumber);
                if (preGeneratedOutline != null) {
                    logger.info("✅ 找到预生成章纲: 第{}章, 方向={}", chapterNumber, preGeneratedOutline.getDirection());
                    sendEvent(emitter, "outline", "📋 使用预生成章纲");
                }
            } catch (Exception e) {
                logger.warn("⚠️ 查询预生成章纲失败: {}", e.getMessage());
            }
        }

        // 推理与意图
        Map<String, Object> plotIntent = null;
        String reasoningPrompt = null;
        String mode = "direct_writing";

        if (preGeneratedOutline != null) {
            // 有章纲：跳过推理，直接用章纲构建 plotIntent
            sendEvent(emitter, "phase", "📋 使用预生成章纲...");
            plotIntent = convertOutlineToIntent(preGeneratedOutline);
            mode = "outline_writing";
        } else {
            // 无章纲：走推理流程
            try {
                sendEvent(emitter, "phase", "🧠 推理本章剧情...");
                plotIntent = plotReasoningService.reasonPlotIntent(context, chapterNumber, aiConfig);
                sendEvent(emitter, "intent", "✅ 剧情推理完成");
                if (plotIntent != null && plotIntent.get("_reasoning_prompt") != null) {
                    reasoningPrompt = String.valueOf(plotIntent.get("_reasoning_prompt"));
                }
            } catch (Exception e) {
                logger.warn("⚠️ 剧情推理失败，将回退到直接写作: {}", e.getMessage());
            }
        }

        List<Map<String, String>> messages;
        if (plotIntent != null && !plotIntent.isEmpty()) {
            sendEvent(emitter, "phase", "✍️ AI创作中（意图驱动）...");
            messages = structuredMessageBuilder.buildMessagesFromIntent(
                    novel, context, plotIntent, chapterNumber, stylePromptFile);
            if (!mode.equals("outline_writing")) {
                mode = "intent_writing";
            }
        } else {
            sendEvent(emitter, "phase", "✍️ AI创作中（直接写作）...");
            messages = structuredMessageBuilder.buildMessagesForDirectWriting(
                    novel, context, chapterNumber, userAdjustment, stylePromptFile);
            mode = "direct_writing";
        }

        logger.info("🔍 构建messages后 - 消息总数: {}", messages.size());

        String generationContextSnapshot = serializeGenerationContext(context, messages, mode);

        // 非流式生成章节内容
        sendEvent(emitter, "phase", "🤖 AI生成中，请稍候...");
        String generatedContent = aiWritingService.generateContentWithMessages(
                messages,
                "chapter_writing",
                aiConfig
        );

        if (generatedContent == null || generatedContent.isEmpty()) {
            throw new RuntimeException("AI生成内容为空，请检查AI配置和提示词");
        }

        // 发送完整内容
        sendEvent(emitter, "content", generatedContent);

        // 保存章节
        sendEvent(emitter, "phase", "💾 保存中...");
        String decisionLog = serializeDecisionLog(context, plotIntent, null, reasoningPrompt, messages, mode);
        Chapter chapter = saveChapter(novel, chapterNumber, generatedContent, generationContextSnapshot, decisionLog, aiConfig);

        // 异步抽取核心状态并入图
        if (coreStateExtractor != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    sendEvent(emitter, "phase", "🔍 抽取核心状态中...");
                    coreStateExtractor.extractAndSaveCoreState(
                            novel.getId(),
                            chapterNumber,
                            generatedContent,
                            chapter.getTitle(),
                            aiConfig
                    );
                    sendEvent(emitter, "extraction", "✅ 核心状态抽取完成");
                } catch (Exception e) {
                    logger.error("核心状态抽取失败（不阻塞章节保存）", e);
                    sendEvent(emitter, "extraction", "⚠️ 核心状态抽取失败: " + e.getMessage());
                }
            });
        }
        // 异步抽取结构化实体并入图
        if (entityExtractionService != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    sendEvent(emitter, "phase", "🔎 抽取结构化实体中...");
                    entityExtractionService.extractAndSave(
                            novel.getId(),
                            chapterNumber,
                            chapter.getTitle(),
                            generatedContent,
                            aiConfig
                    );
                    sendEvent(emitter, "extraction", "✅ 实体抽取完成");
                } catch (Exception e) {
                    logger.error("实体抽取失败（不阻塞章节保存）", e);
                    sendEvent(emitter, "extraction", "⚠️ 实体抽取失败: " + e.getMessage());
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
            Map<String, String> referenceContents,
            SseEmitter emitter) throws Exception {

        logger.info("📚 开始批量生成: novelId={}, 起始章节={}, 数量={}", novelId, startChapter, count);

        List<Chapter> chapters = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Integer currentChapter = startChapter + i;

            sendEvent(emitter, "chapter_start", "开始生成第 " + currentChapter + " 章 (" + (i + 1) + "/" + count + ")");

            try {
                Chapter chapter = generateChapter(novelId, currentChapter, null, aiConfig, stylePromptFile, referenceContents, emitter);
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
     */
    private WritingContext buildDirectWritingContext(Long novelId, Integer chapterNumber, String userAdjustment, Map<String, String> referenceContents) {
        WritingContext.WritingContextBuilder contextBuilder = WritingContext.builder();
        contextBuilder.userAdjustment(userAdjustment);
        if (referenceContents != null && !referenceContents.isEmpty()) {
            contextBuilder.referenceContents(referenceContents);
        }

        try {
            // 1. 获取核心设定（替代大纲）
            Map<String, Object> outlineArgs = new HashMap<>();
            outlineArgs.put("novelId", novelId);
            Object outlineResult = toolRegistry.executeTool("getOutline", outlineArgs);
            if (outlineResult instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> outlineMap = (Map<String, Object>) outlineResult;
                Object core = outlineMap.get("coreSettings");
                if (core instanceof String && org.apache.commons.lang3.StringUtils.isNotBlank((String) core)) {
                    contextBuilder.coreSettings((String) core);
                    logger.info("✅ 已加载核心设定");
                }
            } else if (outlineResult instanceof String) {
                contextBuilder.coreSettings((String) outlineResult);
                logger.info("✅ 已加载核心设定（字符串）");
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

            // 3. 获取最近1章完整内容
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
                Object characterResult = toolRegistry.executeTool("getCharacterProfiles", characterArgs);
                if (characterResult instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> characters = (List<Map<String, Object>>) characterResult;
                    contextBuilder.characterProfiles(characters);
                    logger.info("✅ 已加载{}个角色档案", characters.size());
                }
            }

            // 6. 获取图谱数据（相关事件）
            if (chapterNumber > 1 && graphService != null) {
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
                    __charStates = graphService.getCharacterStates(novelId, 5);
                    if (__charStates != null && !__charStates.isEmpty()) {
                        contextBuilder.characterStates(__charStates);
                        logger.info("✅ 已加载{}个角色状态", __charStates.size());
                    }
                } catch (Exception e) {
                    logger.warn("⚠️ 获取角色状态失败: {}", e.getMessage());
                }
                try {
                    __relationships = graphService.getTopRelationships(novelId, 5);
                    if (__relationships != null && !__relationships.isEmpty()) {
                        contextBuilder.relationshipStates(__relationships);
                        logger.info("✅ 已加载{}条关系状态", __relationships.size());
                    }
                } catch (Exception e) {
                    logger.warn("⚠️ 获取关系状态失败: {}", e.getMessage());
                }
                try {
                    __openQuests = graphService.getOpenQuests(novelId, chapterNumber);
                    if (__openQuests != null && !__openQuests.isEmpty()) {
                        contextBuilder.openQuests(__openQuests);
                        logger.info("✅ 已加载{}个未决任务", __openQuests.size());
                    }
                } catch (Exception e) {
                    logger.warn("⚠️ 获取未决任务失败: {}", e.getMessage());
                }
            }

            // 13. 生成轻量章合同（基于未决任务的本章目标）
            try {
                java.util.Map<String, Object> plan = new java.util.HashMap<>();
                plan.put("chapterNumber", chapterNumber);
                plan.put("estimatedWords", 2500);

                // 基于未决任务推导本章意图
                if (__openQuests != null && !__openQuests.isEmpty()) {
                    StringBuilder purpose = new StringBuilder("推进未决任务：");
                    int limit = Math.min(2, __openQuests.size());
                    for (int i = 0; i < limit; i++) {
                        java.util.Map<String, Object> q = __openQuests.get(i);
                        Object id = q.get("id");
                        Object desc = q.get("description");
                        purpose.append("[").append(id != null ? id : (i + 1)).append("] ");
                        if (desc != null) {
                            String d = desc.toString();
                            purpose.append(d.length() > 24 ? d.substring(0, 24) + "..." : d);
                        }
                        if (i < limit - 1) purpose.append("；");
                    }
                    plan.put("purpose", purpose.toString());
                    plan.put("primaryFocus", "QUEST_PROGRESS");
                }

                contextBuilder.chapterPlan(plan);
            } catch (Exception ignore) {
                // 忽略章合同推导失败
            }

        } catch (Exception e) {
            logger.error("❌ 构建直接写作上下文失败", e);
        }
        return contextBuilder.build();
    }

    /**
     * 保存章节
     */
    private Chapter saveChapter(Novel novel, Integer chapterNumber, String content, String generationContext, String reactDecisionLog, AIConfigRequest aiConfig) {
        Chapter existing = chapterService.getChapterByNovelAndNumber(novel.getId(), chapterNumber);
        Chapter persisted;
        if (existing == null) {
            Chapter chapter = new Chapter();
            chapter.setNovelId(novel.getId());
            chapter.setChapterNumber(chapterNumber);
            chapter.setTitle("第" + chapterNumber + "章");
            chapter.setContent(content);
            chapter.setGenerationContext(generationContext);
            chapter.setReactDecisionLog(reactDecisionLog);
            persisted = chapterService.createChapter(chapter);
        } else {
            Chapter update = new Chapter();
            update.setTitle(existing.getTitle() != null ? existing.getTitle() : "第" + chapterNumber + "章");
            update.setContent(content);
            update.setGenerationContext(generationContext);
            update.setReactDecisionLog(reactDecisionLog);
            persisted = chapterService.updateChapter(existing.getId(), update);
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
            payload = payload.replace("\r\n", "\n").replace("\r", "\n");
            SseEmitter.SseEventBuilder builder = SseEmitter.event()
                    .data(payload, MediaType.TEXT_PLAIN);
            if (StringUtils.hasText(eventType)) {
                builder.name(eventType);
            }
            emitter.send(builder);
        } catch (IllegalStateException ex) {
            logger.warn("SSE已完成，忽略事件: {}", eventType);
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

    private String serializeDecisionLog(
            WritingContext context,
            Map<String, Object> plotIntent,
            String brief,
            String reasoningPrompt,
            List<Map<String, String>> writingMessages,
            String mode
    ) {
        if (context == null) {
            return null;
        }

        Map<String, Object> log = new HashMap<>();
        log.put("timestamp", LocalDateTime.now());
        log.put("chapterNumber", context.getChapterPlan() != null ? context.getChapterPlan().get("chapterNumber") : null);
        log.put("userAdjustment", context.getUserAdjustment());
        if (mode != null) {
            log.put("mode", mode);
        }

        // 🧠 剧情推理结果与提示词
        if (plotIntent != null) {
            log.put("plotReasoning", plotIntent);
        }
        if (reasoningPrompt != null && !reasoningPrompt.isEmpty()) {
            log.put("reasoningPrompt", reasoningPrompt);
        }

        // 📋 章纲（如有）
        if (brief != null) {
            log.put("brief", brief);
            log.put("briefLength", brief.length());
        }

        // ✍️ 写作提示词（messages）
        if (writingMessages != null) {
            log.put("writingMessages", writingMessages);
            log.put("writingMessagesCount", writingMessages.size());
        }

        // 完整的思考和行动记录
        List<AgentThought> thoughts = context.getThoughts();
        if (thoughts != null && !thoughts.isEmpty()) {
            List<Map<String, Object>> detailedThoughts = new ArrayList<>();
            for (AgentThought thought : thoughts) {
                Map<String, Object> thoughtDetail = new HashMap<>();
                thoughtDetail.put("stepNumber", thought.getStepNumber());
                thoughtDetail.put("timestamp", thought.getTimestamp());
                thoughtDetail.put("reasoning", thought.getReasoning());
                thoughtDetail.put("action", thought.getAction());
                thoughtDetail.put("actionArgs", thought.getActionArgs());
                thoughtDetail.put("observation", thought.getObservation());
                thoughtDetail.put("reflection", thought.getReflection());
                thoughtDetail.put("goalAchieved", thought.getGoalAchieved());
                detailedThoughts.add(thoughtDetail);
            }
            log.put("decisionSteps", detailedThoughts);
        }

        // 查询到的各类数据
        Map<String, Object> queriedData = new HashMap<>();
        if (context.getCoreSettings() != null) {
            queriedData.put("core_settings", context.getCoreSettings());
        }
        if (context.getVolumeBlueprint() != null) {
            queriedData.put("volumeBlueprint", context.getVolumeBlueprint());
        }
        if (context.getRecentFullChapters() != null && !context.getRecentFullChapters().isEmpty()) {
            queriedData.put("recentFullChapters", context.getRecentFullChapters().size() + "章");
        }
        if (context.getRecentSummaries() != null && !context.getRecentSummaries().isEmpty()) {
            queriedData.put("recentSummaries", context.getRecentSummaries().size() + "章概要");
        }
        if (context.getCharacterProfiles() != null && !context.getCharacterProfiles().isEmpty()) {
            queriedData.put("characterProfiles", context.getCharacterProfiles().size() + "个角色");
        }
        if (context.getRelevantEvents() != null && !context.getRelevantEvents().isEmpty()) {
            queriedData.put("relevantEvents", context.getRelevantEvents().size() + "个事件");
        }
        if (context.getUnresolvedForeshadows() != null && !context.getUnresolvedForeshadows().isEmpty()) {
            queriedData.put("unresolvedForeshadows", context.getUnresolvedForeshadows().size() + "个伏笔");
        }
        if (context.getConflictArcs() != null && !context.getConflictArcs().isEmpty()) {
            queriedData.put("conflictArcs", context.getConflictArcs().size() + "个冲突弧线");
        }
        if (context.getCharacterArcs() != null && !context.getCharacterArcs().isEmpty()) {
            queriedData.put("characterArcs", context.getCharacterArcs().size() + "个角色弧线");
        }
        if (context.getPlotlineStatus() != null && !context.getPlotlineStatus().isEmpty()) {
            queriedData.put("plotlineStatus", context.getPlotlineStatus().size() + "条情节线");
        }
        if (context.getWorldRules() != null && !context.getWorldRules().isEmpty()) {
            queriedData.put("worldRules", context.getWorldRules().size() + "条世界规则");
        }
        if (context.getNarrativeRhythm() != null) {
            queriedData.put("narrativeRhythm", context.getNarrativeRhythm());
        }
        if (context.getInnovationIdeas() != null && !context.getInnovationIdeas().isEmpty()) {
            queriedData.put("innovationIdeas", context.getInnovationIdeas().size() + "个创新方案");
        }
        log.put("queriedData", queriedData);

        // 章节意图和预期效果
        if (context.getChapterIntent() != null) {
            log.put("chapterIntent", context.getChapterIntent());
        }

        // 统计信息
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalDecisionSteps", thoughts != null ? thoughts.size() : 0);
        statistics.put("toolsInvoked", thoughts != null ? thoughts.stream()
                .map(AgentThought::getAction)
                .filter(action -> !"WRITE".equals(action))
                .distinct()
                .count() : 0);
        statistics.put("dataSourcesQueried", queriedData.size());
        log.put("statistics", statistics);

        try {
            return objectMapper.writeValueAsString(log);
        } catch (Exception e) {
            logger.warn("无法序列化ReAct决策日志，将存储简化版本", e);
            try {
                Map<String, Object> fallbackLog = new HashMap<>();
                fallbackLog.put("timestamp", LocalDateTime.now());
                fallbackLog.put("error", "完整序列化失败: " + e.getMessage());
                return objectMapper.writeValueAsString(fallbackLog);
            } catch (Exception fallback) {
                logger.error("ReAct决策日志序列化完全失败", fallback);
                return "{\"error\":\"序列化失败\",\"timestamp\":\"" + LocalDateTime.now() + "\"}";
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
     * 🆕 清理章节的图谱数据和概要（用于重写章节时）
     */
    private void cleanupChapterMetadata(Long novelId, Integer chapterNumber) {
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
     */
    private Map<String, Object> convertOutlineToIntent(VolumeChapterOutline outline) {
        Map<String, Object> intent = new HashMap<>();
        intent.put("direction", outline.getDirection());

        // 解析 keyPlotPoints（JSON数组）
        if (outline.getKeyPlotPoints() != null) {
            try {
                List<String> points = objectMapper.readValue(outline.getKeyPlotPoints(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<String>>(){});
                intent.put("keyPlotPoints", points);
            } catch (Exception e) {
                logger.warn("解析keyPlotPoints失败: {}", e.getMessage());
                intent.put("keyPlotPoints", new ArrayList<>());
            }
        }

        intent.put("emotionalTone", outline.getEmotionalTone());

        // 解析 foreshadowDetail
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
        }

        intent.put("subplot", outline.getSubplot());

        // 解析 antagonism
        if (outline.getAntagonism() != null) {
            try {
                Map<String, Object> antag = objectMapper.readValue(outline.getAntagonism(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>(){});
                intent.put("antagonism", antag);
            } catch (Exception e) {
                logger.warn("解析antagonism失败: {}", e.getMessage());
            }
        }

        return intent;
    }
}
