package com.novel.shortstory.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.shortstory.entity.ShortNovel;
import com.novel.shortstory.entity.ShortChapter;
import com.novel.shortstory.entity.WorkflowLog;
import com.novel.shortstory.repository.ShortNovelRepository;
import com.novel.shortstory.repository.ShortChapterRepository;
import com.novel.shortstory.repository.WorkflowLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 短篇小说工作流引擎
 * 
 * 核心流程：
 * 1. 生成大纲
 * 2. 拆分章节
 * 3. 循环生成每章：
 *    a) 生成正文
 *    b) AI审稿
 *    c) 如果不通过，重写（最多N次）
 *    d) 如果通过，检查是否需要更新大纲
 *    e) 继续下一章
 * 4. 完成
 */
@Service
public class ShortStoryWorkflowEngine {
    
    private static final Logger logger = LoggerFactory.getLogger(ShortStoryWorkflowEngine.class);
    
    @Autowired
    private ShortNovelRepository novelRepository;
    
    @Autowired
    private ShortChapterRepository chapterRepository;
    
    @Autowired
    private WorkflowLogRepository logRepository;
    
    @Autowired
    private ShortStoryAIService aiService;
    
    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 执行完整工作流
     */
    public void execute(Long novelId) {
        logger.info("🚀 开始执行工作流: novelId={}", novelId);
        
        ShortNovel novel = novelRepository.findById(novelId)
            .orElseThrow(() -> new RuntimeException("小说不存在"));
        
        try {
            // 读取工作流配置中的模型ID
            String modelId = extractModelIdFromConfig(novel.getWorkflowConfig());
            if (modelId != null) {
                aiService.setCurrentModelId(modelId);
                addLog(novelId, null, "INFO", "使用模型: " + modelId);
            }
            
            // 步骤0：生成故事设定（Story Bible）
            if (novel.getStorySetting() == null || novel.getStorySetting().trim().isEmpty()) {
                generateStorySetting(novel);
            }

            // 步骤1：生成大纲
            if (novel.getOutline() == null || novel.getOutline().trim().isEmpty()) {
                generateOutline(novel);
            }
            
            // 步骤2：生成看点标题+核心，并初始化章节元数据
            List<ShortChapter> chapters = chapterRepository.findByNovelIdOrderByChapterNumberAsc(novelId);
            if (chapters.isEmpty()) {
                generateHooksAndInitChapters(novel);
                chapters = chapterRepository.findByNovelIdOrderByChapterNumberAsc(novelId);
            }
            
            // 步骤3：生成导语（黄金开头）
            if (novel.getPrologue() == null || novel.getPrologue().trim().isEmpty()) {
                generatePrologue(novel, chapters);
            }
            
            // 步骤4：循环生成每章
            for (int i = novel.getCurrentChapter() + 1; i <= novel.getChapterCount(); i++) {
                if (Thread.currentThread().isInterrupted()) {
                    logger.info("工作流被中断: novelId={}", novelId);
                    return;
                }
                
                generateSingleChapter(novel, i);
            }
            
            // 完成
            novel.setActiveStep("COMPLETED");
            novel.setStatus("COMPLETED");
            novelRepository.save(novel);
            
            addLog(novelId, null, "SUCCESS", "🎉 全部章节生成完成！");
            sendProgress(novelId, 100, "COMPLETED", "工作流完成");
            
            logger.info("✅ 工作流执行完成: novelId={}", novelId);
            
        } catch (Exception e) {
            logger.error("❌ 工作流执行失败: novelId={}", novelId, e);
            
            novel.setActiveStep("FAILED");
            novel.setStatus("FAILED");
            novel.setErrorMessage(e.getMessage());
            novelRepository.save(novel);
            
            addLog(novelId, null, "ERROR", "工作流失败: " + e.getMessage());
            sendProgress(novelId, -1, "FAILED", e.getMessage());
        } finally {
            // 清除线程局部的模型ID
            aiService.clearCurrentModelId();
        }
    }
    
    /**
     * 从 workflowConfig JSON 中提取 modelId
     */
    private String extractModelIdFromConfig(String workflowConfig) {
        if (workflowConfig == null || workflowConfig.trim().isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> config = objectMapper.readValue(workflowConfig, new TypeReference<Map<String, Object>>() {});
            Object modelId = config.get("modelId");
            return modelId != null ? modelId.toString() : null;
        } catch (Exception e) {
            logger.warn("解析 workflowConfig 失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 生成故事设定（Story Bible）
     */
    @Transactional
    protected void generateStorySetting(ShortNovel novel) {
        novel.setActiveStep("STORY_SETTING");
        // 保持 WORKFLOW_RUNNING，避免前端停止轮询
        novel.setStatus("WORKFLOW_RUNNING");
        novelRepository.save(novel);

        addLog(novel.getId(), null, "INFO", "🧱 开始生成故事设定...");
        sendProgress(novel.getId(), 3, "WORKFLOW_RUNNING", "AI正在生成故事设定");

        String storySetting = aiService.generateStorySetting(
                novel.getTitle(),
                novel.getIdea(),
                novel.getTargetWords(),
                novel.getChapterCount(),
                novel.getUserId()
        );

        novel.setStorySetting(storySetting);
        novelRepository.save(novel);

        addLog(novel.getId(), null, "SUCCESS", "✅ 故事设定生成完成");
        sendProgress(novel.getId(), 6, "WORKFLOW_RUNNING", "故事设定生成完成");
    }

    /**
     * 生成大纲
     */
    @Transactional
    protected void generateOutline(ShortNovel novel) {
        // 标记步骤（用于前端画布高亮）
        novel.setActiveStep("OUTLINE");
        novel.setStatus("GENERATING_OUTLINE");
        novelRepository.save(novel);

        addLog(novel.getId(), null, "INFO", "📝 开始生成大纲...");
        sendProgress(novel.getId(), 8, "GENERATING_OUTLINE", "AI正在生成大纲");
        
        String outline = aiService.generateOutline(
            novel.getIdea(),
            novel.getTargetWords(),
            novel.getChapterCount(),
            novel.getUserId()
        );
        
        novel.setOutline(outline);
        // 保持 WORKFLOW_RUNNING，避免前端停止轮询
        novel.setStatus("WORKFLOW_RUNNING");
        novelRepository.save(novel);
        
        addLog(novel.getId(), null, "SUCCESS", "✅ 大纲生成完成");
        sendProgress(novel.getId(), 12, "WORKFLOW_RUNNING", "大纲生成完成");
    }
    
    /**
     * 生成章节看点（标题+核心）并初始化章节元数据
     */
    @Transactional
    protected void generateHooksAndInitChapters(ShortNovel novel) {
        novel.setActiveStep("HOOKS");
        novel.setStatus("WORKFLOW_RUNNING");
        novelRepository.save(novel);

        addLog(novel.getId(), null, "INFO", "✨ 开始生成章节看点（标题+核心）...");
        sendProgress(novel.getId(), 16, "WORKFLOW_RUNNING", "正在生成章节看点");

        try {
            List<Map<String, Object>> hooks = aiService.generateHooks(
                    novel.getStorySetting() != null ? novel.getStorySetting() : "",
                    novel.getOutline() != null ? novel.getOutline() : "",
                    novel.getChapterCount(),
                    novel.getUserId()
            );

            try {
                novel.setHooksJson(objectMapper.writeValueAsString(hooks));
            } catch (Exception e) {
                logger.warn("hooks_json 序列化失败: {}", e.getMessage());
                novel.setHooksJson(null);
            }
            novelRepository.save(novel);

            Map<Integer, Map<String, Object>> hookByChapter = new HashMap<>();
            for (Map<String, Object> h : hooks) {
                Object numObj = h.get("chapterNumber");
                Integer num = null;
                if (numObj instanceof Number) {
                    num = ((Number) numObj).intValue();
                } else if (numObj != null) {
                    try {
                        num = Integer.parseInt(String.valueOf(numObj));
                    } catch (Exception ignored) {
                    }
                }
                if (num != null) {
                    hookByChapter.put(num, h);
                }
            }

            for (int i = 1; i <= novel.getChapterCount(); i++) {
                Map<String, Object> hook = hookByChapter.get(i);
                String title = hook != null ? String.valueOf(hook.getOrDefault("title", "第" + i + "章")) : ("第" + i + "章");
                String core = hook != null ? String.valueOf(hook.getOrDefault("core", "")) : "";

                ShortChapter chapter = new ShortChapter();
                chapter.setNovelId(novel.getId());
                chapter.setChapterNumber(i);
                chapter.setTitle(title);
                chapter.setBrief(core);
                chapter.setStatus("PENDING");
                chapterRepository.save(chapter);
            }

            addLog(novel.getId(), null, "SUCCESS", "✅ 看点生成完成，章节已初始化");
            sendProgress(novel.getId(), 20, "WORKFLOW_RUNNING", "章节看点生成完成");
        } catch (Exception e) {
            addLog(novel.getId(), null, "ERROR", "看点生成失败，回退到大纲拆分: " + e.getMessage());
            splitChaptersFallback(novel);
        }
    }

    /**
     * 生成导语（黄金开头）
     */
    @Transactional
    protected void generatePrologue(ShortNovel novel, List<ShortChapter> chapters) {
        novel.setActiveStep("PROLOGUE");
        novel.setStatus("WORKFLOW_RUNNING");
        novelRepository.save(novel);

        addLog(novel.getId(), null, "INFO", "✨ 开始生成导语（黄金开头）...");
        sendProgress(novel.getId(), 22, "WORKFLOW_RUNNING", "正在生成导语");

        // 获取第一章的看点/核心作为参考
        String firstChapterHook = "";
        if (!chapters.isEmpty()) {
            ShortChapter firstChapter = chapters.get(0);
            firstChapterHook = firstChapter.getTitle() + "：" + (firstChapter.getBrief() != null ? firstChapter.getBrief() : "");
        }

        String prologue = aiService.generatePrologue(
                novel.getTitle(),
                novel.getIdea(),
                novel.getStorySetting() != null ? novel.getStorySetting() : "",
                novel.getOutline() != null ? novel.getOutline() : "",
                firstChapterHook,
                novel.getUserId()
        );

        novel.setPrologue(prologue);
        novelRepository.save(novel);

        addLog(novel.getId(), null, "SUCCESS", "✅ 导语生成完成");
        sendProgress(novel.getId(), 25, "WORKFLOW_RUNNING", "导语生成完成");
    }

    /**
     * 旧逻辑回退：从大纲拆分章节简述
     */
    @Transactional
    protected void splitChaptersFallback(ShortNovel novel) {
        addLog(novel.getId(), null, "INFO", "📋 开始拆分章节（回退逻辑）...");
        sendProgress(novel.getId(), 15, "WORKFLOW_RUNNING", "正在拆分章节");

        Map<String, String> chapterBriefs = aiService.splitChapters(
                novel.getOutline(),
                novel.getChapterCount(),
                novel.getUserId()
        );

        for (int i = 1; i <= novel.getChapterCount(); i++) {
            ShortChapter chapter = new ShortChapter();
            chapter.setNovelId(novel.getId());
            chapter.setChapterNumber(i);
            chapter.setTitle("第" + i + "章");
            chapter.setBrief(chapterBriefs.getOrDefault(String.valueOf(i), ""));
            chapter.setStatus("PENDING");
            chapterRepository.save(chapter);
        }

        addLog(novel.getId(), null, "SUCCESS", "✅ 章节拆分完成（回退逻辑）");
        sendProgress(novel.getId(), 20, "WORKFLOW_RUNNING", "章节拆分完成");
    }
    
    /**
     * 生成单个章节
     */
    @Transactional
    protected void generateSingleChapter(ShortNovel novel, int chapterNumber) {
        logger.info("开始生成章节: novelId={}, chapter={}", novel.getId(), chapterNumber);

        ShortChapter chapter = chapterRepository.findByNovelIdAndChapterNumber(novel.getId(), chapterNumber)
                .orElseThrow(() -> new RuntimeException("章节不存在: " + chapterNumber));

        // 进度估算：前置(0-20) + 章节(20-95) + 收尾(95-100)
        int progress = 20 + (int) (((chapterNumber - 1) / (double) novel.getChapterCount()) * 75);

        // 获取前文（仅已完成章节）
        String previousContent = buildPreviousContent(novel.getId(), chapterNumber);

        String adjustment = null;

        // 生成-审稿-返工循环
        for (int retry = 0; retry < novel.getMaxRetryPerChapter(); retry++) {
            if (Thread.currentThread().isInterrupted()) {
                logger.info("工作流被中断: novelId={}, chapter={}", novel.getId(), chapterNumber);
                return;
            }

            // 生成阶段
            novel.setActiveStep("CHAPTER_" + chapterNumber + "_GENERATE");
            novel.setCurrentRetryCount(retry);
            novel.setStatus("WORKFLOW_RUNNING");
            novelRepository.save(novel);

            chapter.setStatus(retry == 0 ? "GENERATING" : "REVISING");
            chapter.setLastAdjustment(adjustment);
            chapterRepository.save(chapter);

            addLog(novel.getId(), chapterNumber, "ACTION",
                    retry == 0
                            ? "🖋️ 生成第" + chapterNumber + "章"
                            : "🔄 重写第" + chapterNumber + "章（第" + (retry + 1) + "次）");
            sendProgress(novel.getId(), progress + 3, "WORKFLOW_RUNNING", "生成第" + chapterNumber + "章");

            long startTime = System.currentTimeMillis();
            String outlineForPrompt = buildOutlineForPrompt(novel);
            String content = aiService.generateChapter(
                    outlineForPrompt,
                    previousContent,
                    chapter.getBrief(),
                    novel.getWordsPerChapter(),
                    adjustment,
                    novel.getUserId()
            );
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;

            chapter.setContent(content);
            chapter.setWordCount(content != null ? content.length() : 0);
            chapter.setGenerationTime(elapsed);
            chapter.setStatus("REVIEWING");
            chapterRepository.save(chapter);

            // 通知前端：章节已生成
            sendChapterUpdate(novel.getId(), chapterNumber, "GENERATED", content != null ? content : "");

            // 审稿阶段
            novel.setActiveStep("CHAPTER_" + chapterNumber + "_REVIEW");
            novelRepository.save(novel);

            addLog(novel.getId(), chapterNumber, "ACTION", "🔍 AI审稿中...");
            sendProgress(novel.getId(), progress + 6, "WORKFLOW_RUNNING", "AI审稿第" + chapterNumber + "章");

            Map<String, Object> reviewResult = aiService.reviewChapter(content, chapter.getBrief(), novel.getUserId());
            try {
                chapter.setReviewResult(objectMapper.writeValueAsString(reviewResult));
            } catch (Exception e) {
                logger.error("reviewResult JSON serialization failed", e);
                chapter.setReviewResult("{}");
            }
            chapterRepository.save(chapter);

            int score = reviewResult.get("score") instanceof Number
                    ? ((Number) reviewResult.get("score")).intValue()
                    : 0;
            boolean passed = score >= novel.getMinPassScore();

            addLog(novel.getId(), chapterNumber, "REVIEW",
                    String.format("📊 审稿结果: 得分 %d/10 %s", score, passed ? "✅通过" : "❌未通过"));

            if (!passed) {
                // 将审稿建议回灌到上一阶段，形成“返工指令”
                String comments = String.valueOf(reviewResult.getOrDefault("comments", ""));
                String suggestions = String.valueOf(reviewResult.getOrDefault("suggestions", ""));
                adjustment = "请根据以下审稿建议对本章进行重写（保持设定/大纲一致，不改变已发生事实）：\n"
                        + "- 评价：" + comments + "\n"
                        + "- 建议：" + suggestions + "\n"
                        + "要求：补足动机与因果、语言更流畅、节奏更紧凑、冲突更清晰。";

                addLog(novel.getId(), chapterNumber, "THOUGHT", "♻️ 审稿未通过，已生成返工指令，将重新生成本章。");
                continue;
            }

            // 通过：章节完成
            chapter.setStatus("COMPLETED");
            chapterRepository.save(chapter);

            sendChapterUpdate(novel.getId(), chapterNumber, "COMPLETED", content != null ? content : "");

            // 分析阶段（看点与后续推演）
            novel.setActiveStep("CHAPTER_" + chapterNumber + "_ANALYZE");
            novelRepository.save(novel);

            addLog(novel.getId(), chapterNumber, "THOUGHT", "🧠 看点分析 & 后续推演...");
            sendProgress(novel.getId(), progress + 9, "WORKFLOW_RUNNING", "分析第" + chapterNumber + "章与后续");

            try {
                Map<String, Object> analysis = aiService.analyzeChapter(
                        novel.getStorySetting() != null ? novel.getStorySetting() : "",
                        novel.getOutline() != null ? novel.getOutline() : "",
                        chapterNumber,
                        novel.getChapterCount(),
                        chapter.getTitle(),
                        chapter.getBrief(),
                        content != null ? content : "",
                        novel.getUserId()
                );

                try {
                    chapter.setAnalysisResult(objectMapper.writeValueAsString(analysis));
                } catch (Exception e) {
                    logger.warn("analysisResult JSON serialization failed: {}", e.getMessage());
                }
                chapterRepository.save(chapter);

                // 决策阶段：是否更新大纲/后续看点
                novel.setActiveStep("CHAPTER_" + chapterNumber + "_DECIDE");
                novelRepository.save(novel);

                boolean needOutlineUpdate = Boolean.TRUE.equals(analysis.get("needOutlineUpdate"));
                boolean needHookUpdate = Boolean.TRUE.equals(analysis.get("needHookUpdate"));

                if (Boolean.TRUE.equals(novel.getEnableOutlineUpdate()) && needOutlineUpdate) {
                    addLog(novel.getId(), chapterNumber, "ACTION", "📝 根据分析调整大纲...");
                    String outlineSuggestion = String.valueOf(analysis.getOrDefault("outlineUpdateSuggestion", ""));
                    String outlineUpdateContext = "【本章正文】\n" + (content != null ? content : "")
                            + "\n\n【调整建议】\n" + outlineSuggestion;

                    String updatedOutline = aiService.updateOutline(
                            novel.getOutline(),
                            outlineUpdateContext,
                            chapterNumber,
                            novel.getUserId()
                    );
                    novel.setOutline(updatedOutline);
                    novelRepository.save(novel);
                    addLog(novel.getId(), chapterNumber, "SUCCESS", "✅ 大纲已更新");
                }

                if (needHookUpdate && chapterNumber < novel.getChapterCount()) {
                    String guidance = String.valueOf(analysis.getOrDefault("hookUpdateGuidance", ""));
                    updateFutureHooks(novel, chapterNumber + 1, guidance);
                }

            } catch (Exception e) {
                logger.warn("章节分析/决策阶段失败: {}", e.getMessage());
                addLog(novel.getId(), chapterNumber, "ERROR", "章节分析/决策阶段失败: " + e.getMessage());
            }

            // 提交/封装阶段：更新统计与进度
            novel.setActiveStep("CHAPTER_" + chapterNumber + "_COMMIT");
            novel.setCurrentChapter(chapterNumber);
            novel.setTotalWords(recalculateTotalWords(novel.getId()));
            novelRepository.save(novel);

            addLog(novel.getId(), chapterNumber, "SUCCESS", "✅ 第" + chapterNumber + "章完成（已封装并准备进入下一章）");
            sendProgress(novel.getId(), progress + 12, "WORKFLOW_RUNNING", "第" + chapterNumber + "章完成");
            return;
        }

        // 超过重试次数仍未通过
        chapter.setStatus("FAILED");
        chapterRepository.save(chapter);
        novel.setActiveStep("CHAPTER_" + chapterNumber + "_FAILED");
        novelRepository.save(novel);

        throw new RuntimeException("第" + chapterNumber + "章生成失败，已达最大重试次数");
    }
    
    /**
     * 构建前文上下文（记忆锚点模式）
     * 
     * 优化策略：
     * - 前面章节：使用 chapterSummary（记忆锚点），耍回用截断正文
     * - 最近一章：使用完整正文（确保衔接自然）
     * - 这样可以大幅减少 token 消耗，同时保持连贯性
     */
    private String buildPreviousContent(Long novelId, int currentChapter) {
        if (currentChapter == 1) {
            return "";
        }
        
        List<ShortChapter> previous = chapterRepository.findByNovelIdOrderByChapterNumberAsc(novelId)
            .stream()
            .filter(c -> c.getChapterNumber() < currentChapter && "COMPLETED".equals(c.getStatus()))
            .sorted((a, b) -> a.getChapterNumber() - b.getChapterNumber())
            .toList();
        
        if (previous.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        
        // 如果有多个已完成章节，前面的用记忆锚点，最后一章用完整正文
        int lastIndex = previous.size() - 1;
        
        // 前面章节：记忆锚点（摘要）
        if (lastIndex > 0) {
            sb.append("【前文记忆锚点】\n");
            for (int i = 0; i < lastIndex; i++) {
                ShortChapter c = previous.get(i);
                sb.append("第").append(c.getChapterNumber()).append("章");
                if (c.getTitle() != null && !c.getTitle().isEmpty()) {
                    sb.append(" ").append(c.getTitle());
                }
                sb.append("：");
                
                // 优先使用分析结果中的 chapterSummary
                String summary = extractChapterSummary(c.getAnalysisResult());
                if (summary != null && !summary.isEmpty()) {
                    sb.append(summary);
                } else {
                    // 回退：截取正文前300字作为摘要
                    String content = c.getContent();
                    if (content != null && !content.isEmpty()) {
                        sb.append(content.length() > 300 ? content.substring(0, 300) + "..." : content);
                    } else {
                        sb.append("（无内容）");
                    }
                }
                sb.append("\n\n");
            }
        }
        
        // 最近一章：完整正文（确保衔接自然）
        ShortChapter lastChapter = previous.get(lastIndex);
        sb.append("【上一章完整内容】\n");
        sb.append("第").append(lastChapter.getChapterNumber()).append("章");
        if (lastChapter.getTitle() != null && !lastChapter.getTitle().isEmpty()) {
            sb.append(" ").append(lastChapter.getTitle());
        }
        sb.append("\n");
        sb.append(lastChapter.getContent() != null ? lastChapter.getContent() : "");
        
        return sb.toString();
    }
    
    /**
     * 从分析结果JSON中提取 chapterSummary
     */
    private String extractChapterSummary(String analysisResultJson) {
        if (analysisResultJson == null || analysisResultJson.trim().isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> m = objectMapper.readValue(analysisResultJson, new TypeReference<Map<String, Object>>() {});
            Object s = m.get("chapterSummary");
            return s != null ? String.valueOf(s) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 构建用于章节生成的“设定+大纲”上下文
     */
    private String buildOutlineForPrompt(ShortNovel novel) {
        String outline = novel.getOutline() != null ? novel.getOutline() : "";
        String setting = novel.getStorySetting() != null ? novel.getStorySetting().trim() : "";
        if (!setting.isEmpty()) {
            return "【故事设定】\n" + setting + "\n\n【大纲】\n" + outline;
        }
        return outline;
    }

    /**
     * 重新计算已完成章节的总字数（避免重试/重写导致统计漂移）
     */
    private int recalculateTotalWords(Long novelId) {
        return chapterRepository.findByNovelIdOrderByChapterNumberAsc(novelId)
                .stream()
                .filter(c -> "COMPLETED".equals(c.getStatus()))
                .mapToInt(c -> {
                    if (c.getWordCount() != null) {
                        return c.getWordCount();
                    }
                    return c.getContent() != null ? c.getContent().length() : 0;
                })
                .sum();
    }

    /**
     * 更新后续章节的看点（标题/核心），并同步到章节与 hooks_json
     */
    @Transactional
    protected void updateFutureHooks(ShortNovel novel, int fromChapter, String guidance) {
        if (fromChapter > novel.getChapterCount()) {
            return;
        }

        addLog(novel.getId(), fromChapter, "ACTION", "🧩 更新后续章节看点（标题/核心）...");

        try {
            String currentHooksJson = buildRemainingHooksJson(novel, fromChapter);
            List<Map<String, Object>> updatedHooks = aiService.updateHooks(
                    fromChapter,
                    novel.getChapterCount(),
                    guidance != null ? guidance : "",
                    currentHooksJson,
                    novel.getUserId()
            );

            applyHooksToChapters(novel.getId(), updatedHooks);
            mergeAndSaveHooksJson(novel, updatedHooks);

            addLog(novel.getId(), fromChapter, "SUCCESS", "✅ 后续看点已更新");
        } catch (Exception e) {
            logger.warn("后续看点更新失败: {}", e.getMessage());
            addLog(novel.getId(), fromChapter, "ERROR", "后续看点更新失败: " + e.getMessage());
        }
    }

    private String buildRemainingHooksJson(ShortNovel novel, int fromChapter) {
        // 优先使用 hooks_json（包含 hookPoints/cliffhanger 等信息）
        try {
            if (novel.getHooksJson() != null && !novel.getHooksJson().trim().isEmpty()) {
                List<Map<String, Object>> all = objectMapper.readValue(
                        novel.getHooksJson(),
                        new TypeReference<List<Map<String, Object>>>() {}
                );
                List<Map<String, Object>> filtered = all.stream()
                        .filter(m -> {
                            Integer num = parseChapterNumber(m.get("chapterNumber"));
                            return num != null && num >= fromChapter;
                        })
                        .toList();
                return objectMapper.writeValueAsString(filtered);
            }
        } catch (Exception ignored) {
        }

        // 回退：用数据库里的 title/brief 组装最小 JSON
        List<Map<String, Object>> minimal = chapterRepository.findByNovelIdOrderByChapterNumberAsc(novel.getId())
                .stream()
                .filter(c -> c.getChapterNumber() != null && c.getChapterNumber() >= fromChapter)
                .map(c -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("chapterNumber", c.getChapterNumber());
                    m.put("title", c.getTitle());
                    m.put("core", c.getBrief());
                    return m;
                })
                .toList();

        try {
            return objectMapper.writeValueAsString(minimal);
        } catch (Exception e) {
            return "[]";
        }
    }

    private void applyHooksToChapters(Long novelId, List<Map<String, Object>> hooks) {
        for (Map<String, Object> hook : hooks) {
            Integer num = parseChapterNumber(hook.get("chapterNumber"));
            if (num == null) {
                continue;
            }

            ShortChapter chapter = chapterRepository.findByNovelIdAndChapterNumber(novelId, num).orElse(null);
            if (chapter == null) {
                continue;
            }

            // 只更新未来章节：不覆盖已完成/已有内容的章节
            if ("COMPLETED".equals(chapter.getStatus())) {
                continue;
            }
            if (chapter.getContent() != null && !chapter.getContent().trim().isEmpty()) {
                continue;
            }

            chapter.setTitle(String.valueOf(hook.getOrDefault("title", chapter.getTitle())));
            chapter.setBrief(String.valueOf(hook.getOrDefault("core", chapter.getBrief())));
            chapterRepository.save(chapter);
        }
    }

    private void mergeAndSaveHooksJson(ShortNovel novel, List<Map<String, Object>> updatedHooks) {
        try {
            List<Map<String, Object>> merged;
            if (novel.getHooksJson() != null && !novel.getHooksJson().trim().isEmpty()) {
                merged = objectMapper.readValue(
                        novel.getHooksJson(),
                        new TypeReference<List<Map<String, Object>>>() {}
                );
            } else {
                merged = new java.util.ArrayList<>();
            }

            Map<Integer, Map<String, Object>> updateMap = new HashMap<>();
            for (Map<String, Object> h : updatedHooks) {
                Integer num = parseChapterNumber(h.get("chapterNumber"));
                if (num != null) {
                    updateMap.put(num, h);
                }
            }

            java.util.List<Map<String, Object>> result = new java.util.ArrayList<>();
            for (Map<String, Object> h : merged) {
                Integer num = parseChapterNumber(h.get("chapterNumber"));
                if (num != null && updateMap.containsKey(num)) {
                    result.add(updateMap.remove(num));
                } else {
                    result.add(h);
                }
            }
            result.addAll(updateMap.values());

            result.sort((a, b) -> {
                Integer na = parseChapterNumber(a.get("chapterNumber"));
                Integer nb = parseChapterNumber(b.get("chapterNumber"));
                return Integer.compare(na != null ? na : 0, nb != null ? nb : 0);
            });

            novel.setHooksJson(objectMapper.writeValueAsString(result));
            novelRepository.save(novel);
        } catch (Exception e) {
            logger.warn("合并 hooks_json 失败: {}", e.getMessage());
            try {
                novel.setHooksJson(objectMapper.writeValueAsString(updatedHooks));
                novelRepository.save(novel);
            } catch (Exception ignored) {
            }
        }
    }

    private Integer parseChapterNumber(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 检查并更新大纲
     */
    @Transactional
    protected void checkAndUpdateOutline(ShortNovel novel, int chapterNumber, String chapterContent) {
        // 简化判断：每生成3章后检查一次
        if (chapterNumber % 3 != 0) {
            return;
        }
        
        addLog(novel.getId(), chapterNumber, "THOUGHT", "🤔 检查是否需要调整大纲...");
        
        try {
            String updatedOutline = aiService.updateOutline(
                novel.getOutline(),
                chapterContent,
                chapterNumber,
                novel.getUserId()
            );
            
            novel.setOutline(updatedOutline);
            novelRepository.save(novel);
            
            addLog(novel.getId(), chapterNumber, "ACTION", "📝 大纲已更新");
        } catch (Exception e) {
            logger.warn("大纲更新失败: {}", e.getMessage());
        }
    }
    
    /**
     * 添加日志
     */
    private void addLog(Long novelId, Integer chapterNumber, String type, String content) {
        WorkflowLog log = new WorkflowLog();
        log.setNovelId(novelId);
        log.setChapterNumber(chapterNumber);
        log.setType(type);
        log.setContent(content);
        logRepository.save(log);
        
        // 实时推送日志
        if (messagingTemplate != null) {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", type);
            msg.put("content", content);
            msg.put("chapterNumber", chapterNumber);
            msg.put("timestamp", LocalDateTime.now());
            
            messagingTemplate.convertAndSend("/topic/shortstory/" + novelId + "/logs", msg);
        }
        
        logger.info("[工作流日志] novelId={}, chapter={}, type={}, content={}", 
            novelId, chapterNumber, type, content);
    }
    
    /**
     * 发送进度更新
     */
    private void sendProgress(Long novelId, int percentage, String status, String message) {
        if (messagingTemplate != null) {
            Map<String, Object> progress = new HashMap<>();
            progress.put("percentage", percentage);
            progress.put("status", status);
            progress.put("message", message);
            progress.put("timestamp", LocalDateTime.now());
            
            messagingTemplate.convertAndSend("/topic/shortstory/" + novelId + "/progress", progress);
        }
    }
    
    /**
     * 发送章节更新
     */
    private void sendChapterUpdate(Long novelId, int chapterNumber, String status, String content) {
        if (messagingTemplate != null) {
            Map<String, Object> chapterUpdate = new HashMap<>();
            chapterUpdate.put("chapterNumber", chapterNumber);
            chapterUpdate.put("status", status);
            chapterUpdate.put("content", content);
            chapterUpdate.put("wordCount", content.length());
            chapterUpdate.put("timestamp", LocalDateTime.now());
            
            messagingTemplate.convertAndSend("/topic/shortstory/" + novelId + "/chapters", chapterUpdate);
        }
    }
}
