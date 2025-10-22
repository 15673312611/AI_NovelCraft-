package com.novel.controller;

import com.novel.common.Result;
import com.novel.domain.entity.Novel;
import com.novel.domain.entity.Chapter;
import com.novel.domain.entity.NovelVolume;
import com.novel.dto.AIConfigRequest;
import com.novel.service.NovelCraftAIService;
import com.novel.service.NovelService;
import com.novel.service.NovelMemoryService;
import com.novel.service.ChapterService;
import com.novel.service.ChapterSummaryService;
import com.novel.service.LongNovelMemoryManager;
import com.novel.service.NovelVolumeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * NovelCraft AI Controller
 * 完整的AI Agent长篇创作系统API接口
 *
 * 基于6大模块的闭环工作流：
 * 1️⃣ 动态大纲引擎 - 可扩展的故事架构
 * 2️⃣ 章节拆解器 - 精细化任务分解
 * 3️⃣ AI写作Agent - 智能内容生成
 * 4️⃣ 记忆库系统 - 一致性保证
 * 5️⃣ 反馈建议系统 - 主动创作指导
 * 6️⃣ 用户决策接口 - 方向控制
 */
@RestController
@RequestMapping("/novel-craft")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}, allowCredentials = "true")
public class NovelCraftController {

    private static final Logger logger = LoggerFactory.getLogger(NovelCraftController.class);

    @Autowired
    private NovelCraftAIService novelCraftAIService;

    @Autowired
    private NovelService novelService;

    @Autowired
    private NovelMemoryService novelMemoryService;

    @Autowired
    private ChapterService chapterService;

    @Autowired
    private ChapterSummaryService chapterSummaryService;

    @Autowired
    private LongNovelMemoryManager longNovelMemoryManager;

    @Autowired
    private NovelVolumeService novelVolumeService;

    // ================================
    // 1️⃣ 动态大纲引擎 API
    // ================================

    /**
     * 初始化动态大纲系统
     * POST /novel-craft/{novelId}/outline/init
     */
    @PostMapping("/{novelId}/outline/init")
    public Result<Map<String, Object>> initializeDynamicOutline(
            @PathVariable Long novelId,
            @RequestBody Map<String, String> request) {

        try {
            Novel novel = novelService.getById(novelId);
            if (novel == null) {
                return Result.error("小说不存在");
            }

            String basicIdea = request.get("basicIdea");
            if (basicIdea == null || basicIdea.trim().isEmpty()) {
                return Result.error("请提供基本创作构思");
            }

            logger.info("🚀 初始化动态大纲: 小说ID={}, 构思长度={}", novelId, basicIdea.length());

            Map<String, Object> outline = novelCraftAIService.initializeDynamicOutline(novel, basicIdea);

            // 初始化记忆库
            Map<String, Object> memoryBank = initializeMemoryBank(novel, outline);

            Map<String, Object> result = new HashMap<>();
            result.put("outline", outline);
            result.put("memoryBank", memoryBank);
            result.put("status", "dynamic_outline_initialized");
            result.put("nextStep", "expand_outline");

            return Result.success(result);

        } catch (Exception e) {
            logger.error("初始化动态大纲失败", e);
            return Result.error("初始化失败: " + e.getMessage());
        }
    }

    /**
     * 动态扩展大纲
     * POST /novel-craft/{novelId}/outline/expand
     */
    @PostMapping("/{novelId}/outline/expand")
    public Result<Map<String, Object>> expandOutlineDynamically(
            @PathVariable Long novelId,
            @RequestBody Map<String, Object> request) {

        try {
            Novel novel = novelService.getById(novelId);
            if (novel == null) {
                return Result.error("小说不存在");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> currentOutline = (Map<String, Object>) request.get("currentOutline");
            Integer currentChapter = (Integer) request.getOrDefault("currentChapter", 1);
            String existingContent = (String) request.getOrDefault("existingContent", "");
            String userDirection = (String) request.get("userDirection");

            if (currentOutline == null) {
                return Result.error("当前大纲信息缺失");
            }

            logger.info("🌱 动态扩展大纲: 小说ID={}, 当前章节={}", novelId, currentChapter);

            Map<String, Object> expandedOutline = novelCraftAIService.expandOutlineDynamically(
                novel, currentOutline, currentChapter, existingContent, userDirection
            );

            Map<String, Object> result = new HashMap<>();
            result.put("expandedOutline", expandedOutline);
            result.put("expandedAt", new Date());
            result.put("nextStep", "decompose_chapters");

            return Result.success(result);

        } catch (Exception e) {
            logger.error("动态扩展大纲失败", e);
            return Result.error("扩展失败: " + e.getMessage());
        }
    }

    /**
     * 大纲确认和调整
     * POST /novel-craft/{novelId}/outline/adjust
     */
    @PostMapping("/{novelId}/outline/adjust")
    public Result<Map<String, Object>> adjustOutline(
            @PathVariable Long novelId,
            @RequestBody Map<String, Object> request) {

        try {
            Novel novel = novelService.getById(novelId);
            if (novel == null) {
                return Result.error("小说不存在");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> currentOutline = (Map<String, Object>) request.get("currentOutline");
            String adjustmentRequest = (String) request.get("adjustmentRequest");
            String basicIdea = (String) request.get("basicIdea");

            if (currentOutline == null || adjustmentRequest == null) {
                return Result.error("缺少必要参数");
            }

            logger.info("🔄 调整大纲: 小说ID={}, 调整要求长度={}", novelId, adjustmentRequest.length());

            Map<String, Object> adjustedOutline = novelCraftAIService.adjustOutlineWithFeedback(
                novel, currentOutline, adjustmentRequest, basicIdea
            );

            Map<String, Object> result = new HashMap<>();
            result.put("adjustedOutline", adjustedOutline);
            result.put("adjustedAt", new Date());
            result.put("status", "adjusted");

            return Result.success(result);

        } catch (Exception e) {
            logger.error("大纲调整失败", e);
            return Result.error("调整失败: " + e.getMessage());
        }
    }

    // ================================
    // 2️⃣ 章节拆解器 API
    // ================================

    /**
     * 智能章节拆解
     * POST /novel-craft/{novelId}/chapters/decompose
     */
    @PostMapping("/{novelId}/chapters/decompose")
    public Result<List<Map<String, Object>>> decomposeChapters(
            @PathVariable Long novelId,
            @RequestBody Map<String, Object> request) {

        try {
            Novel novel = novelService.getById(novelId);
            if (novel == null) {
                return Result.error("小说不存在");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> outline = (Map<String, Object>) request.get("outline");
            Integer startChapter = (Integer) request.getOrDefault("startChapter", 1);
            Integer targetCount = (Integer) request.getOrDefault("targetCount", 20);
            String focusDirection = (String) request.get("focusDirection");

            if (outline == null) {
                return Result.error("大纲信息缺失");
            }

            // 限制拆解数量
            targetCount = Math.min(Math.max(targetCount, 5), 200);

            logger.info("🔧 智能章节拆解: 小说ID={}, 起始章节={}, 拆解数量={}", novelId, startChapter, targetCount);

            List<Map<String, Object>> chapterPlans = novelCraftAIService.decomposeChaptersIntelligently(
                outline, startChapter, targetCount, focusDirection
            );

            return Result.success(chapterPlans);

        } catch (Exception e) {
            logger.error("章节拆解失败", e);
            return Result.error("拆解失败: " + e.getMessage());
        }
    }

    // ================================
    // 3️⃣ AI写作Agent API
    // ================================

    /**
     * 执行章节写作
     * POST /novel-craft/{novelId}/write-chapter
     */
    @PostMapping("/{novelId}/write-chapter")
    public Result<Map<String, Object>> executeChapterWriting(
            @PathVariable Long novelId,
            @RequestBody Map<String, Object> request) {

        try {
            Novel novel = novelService.getById(novelId);
            if (novel == null) {
                return Result.error("小说不存在");
            }

            // 解析章节号：优先取顶层 chapterNumber，其次取 chapterPlan.chapterNumber，最后默认 1
            Integer chapterNumber = null;
            Object chapterNumberObj = request.get("chapterNumber");
            if (chapterNumberObj instanceof Number) {
                chapterNumber = ((Number) chapterNumberObj).intValue();
            }
            Map<String, Object> requestPlan = (Map<String, Object>) request.get("chapterPlan");
            if (chapterNumber == null && requestPlan != null) {
                Object planCn = requestPlan.get("chapterNumber");
                if (planCn instanceof Number) {
                    chapterNumber = ((Number) planCn).intValue();
                }
            }
            if (chapterNumber == null) {
                chapterNumber = 1;
            }

            // 章节规划：优先使用前端传入的 chapterPlan，否则按章节号从库中生成
            Map<String, Object> chapterPlan = requestPlan != null ? new HashMap<>(requestPlan) :
                    novelMemoryService.generateChapterPlan(novelId, chapterNumber);
            chapterPlan.put("chapterNumber", chapterNumber);

            // 记忆库从数据库构建（确保使用最新状态）
            Map<String, Object> memoryBank = novelMemoryService.buildMemoryBankFromDatabase(novelId);

            // 附加当前卷的大纲上下文，便于上下文构建
            try {
                List<NovelVolume> volumes = novelVolumeService.getVolumesByNovelId(novelId);
                if (volumes != null && !volumes.isEmpty()) {
                    for (NovelVolume v : volumes) {
                        if (v.getChapterStart() != null && v.getChapterEnd() != null
                                && chapterNumber >= v.getChapterStart() && chapterNumber <= v.getChapterEnd()) {



                            Map<String, Object> vol = new HashMap<>();
                            vol.put("id", v.getId());
                            vol.put("title", v.getTitle());
                            vol.put("theme", v.getTheme());
                            vol.put("description", v.getDescription());
                            vol.put("contentOutline", v.getContentOutline());
                            vol.put("chapterStart", v.getChapterStart());
                            vol.put("chapterEnd", v.getChapterEnd());
                            memoryBank.put("currentVolumeOutline", vol);
                            break;
                        }
                    }
                }
            } catch (Exception ignore) {}

            String userAdjustment = (String) request.get("userAdjustment");

            logger.info("✍️ 执行章节写作: 小说ID={}, 章节={}", novelId, chapterNumber);

            Map<String, Object> writingResult = novelCraftAIService.executeChapterWriting(
                novel, chapterPlan, memoryBank, userAdjustment
            );

            // 持久化章节与概括，确保第2章起能查到前情
            try {
                String content = (String) writingResult.get("content");
                if (content != null && !content.trim().isEmpty()) {
                    Chapter chapter = new Chapter();
                    chapter.setNovelId(novelId);
                    chapter.setChapterNumber(chapterNumber);
                    String title = (String) writingResult.getOrDefault("title", chapterPlan.get("title"));
                    chapter.setTitle(title);
                    chapter.setContent(content);
                    Object wcObj = writingResult.get("wordCount");
                    Integer wc = (wcObj instanceof Number) ? ((Number) wcObj).intValue() : content.length();
                    chapter.setWordCount(wc);
                    chapterService.createChapter(chapter);

                    try {
                        String summary = chapterSummaryService.generateChapterSummary(chapter);
                        chapterSummaryService.saveChapterSummary(novelId, chapterNumber, summary);
                    } catch (Exception ignore) {}
                }
            } catch (Exception e) {
                logger.warn("章节与概括持久化失败（不影响返回）: {}", e.getMessage());
            }

            // 同步更新长篇记忆库（并落库）
            Map<String, Object> updatedMemoryBank = longNovelMemoryManager.updateMemoryFromChapter(
                novelId, chapterNumber, (String) writingResult.get("content"), memoryBank
            );

            // 执行一致性检查
            Map<String, Object> consistencyReport = novelCraftAIService.performConsistencyCheck(
                novel, writingResult, updatedMemoryBank
            );

            Map<String, Object> result = new HashMap<>();
            result.put("writingResult", writingResult);
            result.put("updatedMemoryBank", updatedMemoryBank);
            result.put("consistencyReport", consistencyReport);
            result.put("nextStep", "review_and_continue");

            return Result.success(result);

        } catch (Exception e) {
            logger.error("章节写作失败", e);
            return Result.error("写作失败: " + e.getMessage());
        }
    }

    /**
     * 流式章节写作
     * POST /novel-craft/{novelId}/write-chapter-stream
     */
    @PostMapping(value = "/{novelId}/write-chapter-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeStreamingChapterWriting(
            @PathVariable Long novelId,
            @RequestBody Map<String, Object> request) {

        SseEmitter emitter = new SseEmitter(300000L); // 5分钟超时

        try {
            Novel novel = novelService.getById(novelId);
            if (novel == null) {
                emitter.send(SseEmitter.event().name("error").data("小说不存在"));
                emitter.completeWithError(new IOException("Novel not found"));
                return emitter;
            }

            // 解析章节号：优先顶层，其次 chapterPlan 内
            Integer chapterNumber = null;
            Object chapterNumberObj = request.get("chapterNumber");
            if (chapterNumberObj instanceof Number) {
                chapterNumber = ((Number) chapterNumberObj).intValue();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> requestPlan = (Map<String, Object>) request.get("chapterPlan");
            if (chapterNumber == null && requestPlan != null) {
                Object planCn = requestPlan.get("chapterNumber");
                if (planCn instanceof Number) {
                    chapterNumber = ((Number) planCn).intValue();
                }
            }
            if (chapterNumber == null) {
                chapterNumber = 1;
            }

            // 后端自行查库装配章节规划
            Map<String, Object> chapterPlan = requestPlan != null ? new HashMap<>(requestPlan) :
                    novelMemoryService.generateChapterPlan(novelId, chapterNumber);
            chapterPlan.put("chapterNumber", chapterNumber);

            // 后端自行查库装配记忆库
            Map<String, Object> memoryBank = novelMemoryService.buildMemoryBankFromDatabase(novelId);

            // 附加当前卷的大纲上下文，便于上下文构建（流式）
            try {
                List<NovelVolume> volumes = novelVolumeService.getVolumesByNovelId(novelId);
                if (volumes != null && !volumes.isEmpty()) {
                    for (NovelVolume v : volumes) {
                        if (v.getChapterStart() != null && v.getChapterEnd() != null
                                && chapterNumber >= v.getChapterStart() && chapterNumber <= v.getChapterEnd()) {
                            Map<String, Object> vol = new HashMap<>();
                            vol.put("id", v.getId());
                            vol.put("title", v.getTitle());
                            vol.put("theme", v.getTheme());
                            vol.put("description", v.getDescription());
                            vol.put("contentOutline", v.getContentOutline());
                            vol.put("chapterStart", v.getChapterStart());
                            vol.put("chapterEnd", v.getChapterEnd());
                            memoryBank.put("currentVolumeOutline", vol);
                            break;
                        }
                    }
                }
            } catch (Exception ignore) {}

            String userAdjustment = (String) request.get("userAdjustment");
            String model = (String) request.get("model"); // 获取前端传递的模型参数
            Long promptTemplateId = null; // 获取前端传递的提示词模板ID
            if (request.get("promptTemplateId") != null) {
                if (request.get("promptTemplateId") instanceof Number) {
                    promptTemplateId = ((Number) request.get("promptTemplateId")).longValue();
                }
            }

            // 解析AI配置（前端withAIConfig是扁平化的，直接从根级别读取）
            AIConfigRequest aiConfig = new AIConfigRequest();
            if (request.containsKey("provider")) {
                // 从根级别直接读取（扁平化格式）
                aiConfig.setProvider((String) request.get("provider"));
                aiConfig.setApiKey((String) request.get("apiKey"));
                aiConfig.setModel((String) request.get("model"));
                aiConfig.setBaseUrl((String) request.get("baseUrl"));
                
                logger.info("✅ 流式章节写作 - 收到AI配置: provider={}, model={}", 
                    aiConfig.getProvider(), aiConfig.getModel());
            } else if (request.get("aiConfig") instanceof Map) {
                // 兼容旧的嵌套格式
                @SuppressWarnings("unchecked")
                Map<String, String> aiConfigMap = (Map<String, String>) request.get("aiConfig");
                aiConfig.setProvider(aiConfigMap.get("provider"));
                aiConfig.setApiKey(aiConfigMap.get("apiKey"));
                aiConfig.setModel(aiConfigMap.get("model"));
                aiConfig.setBaseUrl(aiConfigMap.get("baseUrl"));
            }

            if (!aiConfig.isValid()) {
                logger.error("❌ 流式章节写作 - AI配置无效: {}", request);
                emitter.send(SseEmitter.event().name("error").data("AI配置无效，请先在设置页面配置AI服务"));
                emitter.completeWithError(new IOException("AI配置无效"));
                return emitter;
            }

            logger.info("✍️ 开始流式章节写作: 小说ID={}, 章节={}, AI服务商={}, 模型={}, 模板ID={}", 
                novelId, chapterNumber, aiConfig.getProvider(), aiConfig.getModel(), promptTemplateId != null ? promptTemplateId : "默认");

            // 发送开始事件
            emitter.send(SseEmitter.event().name("start").data("开始写作章节 " + chapterNumber));

            // 异步执行流式写作（使用新版多阶段生成）
            final Long finalTemplateId = promptTemplateId;
            CompletableFuture.runAsync(() -> {
                try {
                    // ✅ 使用新版多阶段生成（构思→判断→写作）
                    novelCraftAIService.executeMultiStageStreamingChapterWriting(
                        novel, chapterPlan, memoryBank, userAdjustment, emitter, aiConfig, finalTemplateId
                    );
                } catch (Exception e) {
                    logger.error("流式章节写作失败", e);
                    try {
                        emitter.send(SseEmitter.event().name("error").data("写作失败: " + e.getMessage()));
                        emitter.completeWithError(e);
                    } catch (IOException ex) {
                        logger.error("发送错误事件失败", ex);
                    }
                }
            });

        } catch (Exception e) {
            logger.error("流式章节写作初始化失败", e);
            try {
                emitter.send(SseEmitter.event().name("error").data("初始化失败: " + e.getMessage()));
                emitter.completeWithError(e);
            } catch (IOException ex) {
                logger.error("发送错误事件失败", ex);
            }
        }

        return emitter;
    }

    /**
     * 批量写作多个章节
     * POST /novel-craft/{novelId}/write-batch
     */
    @PostMapping("/{novelId}/write-batch")
    public Result<Map<String, Object>> batchWriteChapters(
            @PathVariable Long novelId,
            @RequestBody Map<String, Object> request) {

        try {
            Novel novel = novelService.getById(novelId);
            if (novel == null) {
                return Result.error("小说不存在");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> chapterPlans = (List<Map<String, Object>>) request.get("chapterPlans");
            @SuppressWarnings("unchecked")
            Map<String, Object> memoryBank = (Map<String, Object>) request.get("memoryBank");
            Integer batchSize = (Integer) request.getOrDefault("batchSize", 5);

            if (chapterPlans == null || chapterPlans.isEmpty()) {
                return Result.error("章节规划列表为空");
            }

            // 限制批量大小
            batchSize = Math.min(Math.max(batchSize, 1), 10);
            List<Map<String, Object>> targetPlans = chapterPlans.subList(0, Math.min(batchSize, chapterPlans.size()));

            logger.info("📝 批量章节写作: 小说ID={}, 批量大小={}", novelId, targetPlans.size());

            List<Map<String, Object>> writingResults = new ArrayList<>();
            Map<String, Object> currentMemoryBank = new HashMap<>(memoryBank);

            for (Map<String, Object> plan : targetPlans) {
                try {
                    // 执行单章写作
                    Map<String, Object> writingResult = novelCraftAIService.executeChapterWriting(
                        novel, plan, currentMemoryBank, null
                    );

                    // 更新记忆库
                    currentMemoryBank = novelCraftAIService.updateMemoryBank(
                        currentMemoryBank, writingResult
                    );

                    writingResults.add(writingResult);

                    Thread.sleep(1000); // 防止API调用过快

                } catch (Exception e) {
                    logger.warn("批量写作中第{}章失败: {}", plan.get("chapterNumber"), e.getMessage());
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("writingResults", writingResults);
            result.put("finalMemoryBank", currentMemoryBank);
            result.put("successCount", writingResults.size());
            result.put("totalCount", targetPlans.size());

            return Result.success(result);

        } catch (Exception e) {
            logger.error("批量章节写作失败", e);
            return Result.error("批量写作失败: " + e.getMessage());
        }
    }

    // ================================
    // 4️⃣ 记忆库与质检 API
    // ================================

    /**
     * 手动更新记忆库
     * POST /novel-craft/{novelId}/memory/update
     */
    @PostMapping("/{novelId}/memory/update")
    public Result<Map<String, Object>> updateMemoryBank(
            @PathVariable Long novelId,
            @RequestBody Map<String, Object> request) {

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> memoryBank = (Map<String, Object>) request.get("memoryBank");
            @SuppressWarnings("unchecked")
            Map<String, Object> newChapter = (Map<String, Object>) request.get("newChapter");

            if (memoryBank == null || newChapter == null) {
                return Result.error("参数缺失");
            }

            logger.info("🧠 更新记忆库: 小说ID={}", novelId);

            Map<String, Object> updatedMemoryBank = novelCraftAIService.updateMemoryBank(
                memoryBank, newChapter
            );

            return Result.success(updatedMemoryBank);

        } catch (Exception e) {
            logger.error("更新记忆库失败", e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 执行一致性检查
     * POST /novel-craft/{novelId}/consistency/check
     */
    @PostMapping("/{novelId}/consistency/check")
    public Result<Map<String, Object>> performConsistencyCheck(
            @PathVariable Long novelId,
            @RequestBody Map<String, Object> request) {

        try {
            Novel novel = novelService.getById(novelId);
            if (novel == null) {
                return Result.error("小说不存在");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> newChapter = (Map<String, Object>) request.get("newChapter");
            @SuppressWarnings("unchecked")
            Map<String, Object> memoryBank = (Map<String, Object>) request.get("memoryBank");

            if (newChapter == null || memoryBank == null) {
                return Result.error("参数缺失");
            }

            logger.info("🔍 执行一致性检查: 小说ID={}", novelId);

            Map<String, Object> consistencyReport = novelCraftAIService.performConsistencyCheck(
                novel, newChapter, memoryBank
            );

            return Result.success(consistencyReport);

        } catch (Exception e) {
            logger.error("一致性检查失败", e);
            return Result.error("检查失败: " + e.getMessage());
        }
    }

    // ================================
    // 5️⃣ 反馈建议系统 API
    // ================================

    /**
     * 生成智能创作建议
     * POST /novel-craft/{novelId}/suggestions/generate
     */
    @PostMapping("/{novelId}/suggestions/generate")
    public Result<Map<String, Object>> generateIntelligentSuggestions(
            @PathVariable Long novelId,
            @RequestBody Map<String, Object> request) {

        try {
            Novel novel = novelService.getById(novelId);
            if (novel == null) {
                return Result.error("小说不存在");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> memoryBank = (Map<String, Object>) request.get("memoryBank");
            Integer currentChapter = (Integer) request.getOrDefault("currentChapter", 1);
            String recentTrends = (String) request.get("recentTrends");

            if (memoryBank == null) {
                return Result.error("记忆库信息缺失");
            }

            logger.info("💡 生成智能建议: 小说ID={}, 当前章节={}", novelId, currentChapter);

            Map<String, Object> suggestions = novelCraftAIService.generateIntelligentSuggestions(
                novel, memoryBank, currentChapter, recentTrends
            );

            return Result.success(suggestions);

        } catch (Exception e) {
            logger.error("生成智能建议失败", e);
            return Result.error("生成建议失败: " + e.getMessage());
        }
    }

    /**
     * 获取主动提醒
     * GET /novel-craft/{novelId}/reminders/{currentChapter}
     */
    @GetMapping("/{novelId}/reminders/{currentChapter}")
    public Result<List<Map<String, Object>>> getProactiveReminders(
            @PathVariable Long novelId,
            @PathVariable Integer currentChapter) {

        try {
            // 对于GET请求，我们创建一个空的记忆库，或者可以从缓存/数据库获取
            Map<String, Object> memoryBank = new HashMap<>();

            logger.info("🔔 获取主动提醒: 小说ID={}, 章节={}", novelId, currentChapter);

            List<Map<String, Object>> reminders = novelCraftAIService.generateProactiveReminders(
                memoryBank, currentChapter
            );

            return Result.success(reminders);

        } catch (Exception e) {
            logger.error("获取主动提醒失败", e);
            return Result.error("获取提醒失败: " + e.getMessage());
        }
    }

    // ================================
    // 6️⃣ 用户决策接口 API
    // ================================

    /**
     * 智能对话交互
     * POST /novel-craft/{novelId}/dialogue
     */
    @PostMapping("/{novelId}/dialogue")
    public Result<Map<String, Object>> intelligentDialogue(
            @PathVariable Long novelId,
            @RequestBody Map<String, Object> request) {

        try {
            Novel novel = novelService.getById(novelId);
            if (novel == null) {
                return Result.error("小说不存在");
            }

            String userMessage = (String) request.get("userMessage");
            @SuppressWarnings("unchecked")
            Map<String, Object> memoryBank = (Map<String, Object>) request.getOrDefault("memoryBank", new HashMap<>());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> chatHistory = (List<Map<String, Object>>)
                request.getOrDefault("chatHistory", new ArrayList<>());

            if (userMessage == null || userMessage.trim().isEmpty()) {
                return Result.error("对话内容不能为空");
            }

            logger.info("💬 智能对话: 小说ID={}, 消息长度={}", novelId, userMessage.length());

            Map<String, Object> dialogueResult = novelCraftAIService.intelligentDialogue(
                novel, memoryBank, userMessage, chatHistory
            );

            return Result.success(dialogueResult);

        } catch (Exception e) {
            logger.error("智能对话失败", e);
            return Result.error("对话失败: " + e.getMessage());
        }
    }

    /**
     * 执行用户决策
     * POST /novel-craft/{novelId}/decision
     */
    @PostMapping("/{novelId}/decision")
    public Result<Map<String, Object>> executeUserDecision(
            @PathVariable Long novelId,
            @RequestBody Map<String, Object> request) {

        try {
            Novel novel = novelService.getById(novelId);
            if (novel == null) {
                return Result.error("小说不存在");
            }

            String decisionType = (String) request.get("decisionType");
            @SuppressWarnings("unchecked")
            Map<String, Object> decisionParams = (Map<String, Object>) request.get("decisionParams");
            @SuppressWarnings("unchecked")
            Map<String, Object> memoryBank = (Map<String, Object>) request.get("memoryBank");

            if (decisionType == null || decisionParams == null || memoryBank == null) {
                return Result.error("参数缺失");
            }

            logger.info("🎮 执行用户决策: 小说ID={}, 决策类型={}", novelId, decisionType);

            Map<String, Object> decisionResult = novelCraftAIService.executeUserDecision(
                novel, memoryBank, decisionType, decisionParams
            );

            return Result.success(decisionResult);

        } catch (Exception e) {
            logger.error("执行用户决策失败", e);
            return Result.error("决策执行失败: " + e.getMessage());
        }
    }

    // ================================
    // 综合工作流 API
    // ================================

    /**
     * 启动完整AI创作工作流
     * POST /novel-craft/{novelId}/workflow/start
     */
    @PostMapping("/{novelId}/workflow/start")
    public Result<Map<String, Object>> startCompleteWorkflow(
            @PathVariable Long novelId,
            @RequestBody Map<String, Object> request) {

        try {
            Novel novel = novelService.getById(novelId);
            if (novel == null) {
                return Result.error("小说不存在");
            }

            String basicIdea = (String) request.get("basicIdea");
            Integer initialChapterCount = (Integer) request.getOrDefault("initialChapterCount", 10);

            if (basicIdea == null || basicIdea.trim().isEmpty()) {
                return Result.error("请提供基本创作构思");
            }

            logger.info("🚀 启动完整AI创作工作流: 小说ID={}", novelId);

            Map<String, Object> workflowResult = new HashMap<>();

            // 步骤1: 初始化动态大纲
            Map<String, Object> outline = novelCraftAIService.initializeDynamicOutline(novel, basicIdea);
            workflowResult.put("outline", outline);

            // 步骤2: 初始化记忆库
            Map<String, Object> memoryBank = initializeMemoryBank(novel, outline);
            workflowResult.put("memoryBank", memoryBank);

            // 步骤3: 生成初始章节规划
            List<Map<String, Object>> chapterPlans = novelCraftAIService.decomposeChaptersIntelligently(
                outline, 1, initialChapterCount, "平衡开篇"
            );
            workflowResult.put("chapterPlans", chapterPlans);

            // 步骤4: 生成初始建议
            Map<String, Object> suggestions = novelCraftAIService.generateIntelligentSuggestions(
                novel, memoryBank, 1, "项目启动"
            );
            workflowResult.put("suggestions", suggestions);

            workflowResult.put("workflowStatus", "initialized");
            workflowResult.put("nextSteps", Arrays.asList(
                "review_outline", "adjust_chapters", "start_writing", "dialogue_with_ai"
            ));

            return Result.success(workflowResult);

        } catch (Exception e) {
            logger.error("启动完整工作流失败", e);
            return Result.error("工作流启动失败: " + e.getMessage());
        }
    }

    /**
     * 获取工作流状态
     * GET /novel-craft/{novelId}/workflow/status
     */
    @GetMapping("/{novelId}/workflow/status")
    public Result<Map<String, Object>> getWorkflowStatus(@PathVariable Long novelId) {
        try {
            Novel novel = novelService.getById(novelId);
            if (novel == null) {
                return Result.error("小说不存在");
            }

            Map<String, Object> status = new HashMap<>();
            status.put("novelId", novelId);
            status.put("title", novel.getTitle());
            status.put("genre", novel.getGenre());
            status.put("currentStatus", novel.getStatus());

            // 模拟工作流状态
            status.put("workflowStage", "writing_active");
            status.put("completedModules", Arrays.asList("outline", "chapters", "memory"));
            status.put("activeModules", Arrays.asList("writing", "suggestions"));
            status.put("lastActivity", new Date());

            return Result.success(status);

        } catch (Exception e) {
            logger.error("获取工作流状态失败", e);
            return Result.error("获取状态失败: " + e.getMessage());
        }
    }

    // ================================
    // 辅助方法
    // ================================

    /**
     * 初始化记忆库
     */
    private Map<String, Object> initializeMemoryBank(Novel novel, Map<String, Object> outline) {
        Map<String, Object> memoryBank = new HashMap<>();

        // 基础信息
        memoryBank.put("novelId", novel.getId());
        memoryBank.put("title", novel.getTitle());
        memoryBank.put("genre", novel.getGenre());
        memoryBank.put("createdAt", new Date());

        // 核心记忆结构
        memoryBank.put("characters", new HashMap<>());
        memoryBank.put("locations", new HashMap<>());
        memoryBank.put("worldSettings", new HashMap<>());
        memoryBank.put("foreshadowing", new ArrayList<>());
        memoryBank.put("plotThreads", new ArrayList<>());
        memoryBank.put("chapterSummaries", new ArrayList<>());
        memoryBank.put("relationships", new HashMap<>());
        memoryBank.put("timeline", new ArrayList<>());

        // 状态信息
        memoryBank.put("lastUpdated", new Date());
        memoryBank.put("version", 1);
        memoryBank.put("consistency_score", 10.0);

        return memoryBank;
    }
}