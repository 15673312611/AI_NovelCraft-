package com.novel.script.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.script.entity.VideoScript;
import com.novel.script.entity.VideoScriptEpisode;
import com.novel.script.entity.VideoScriptLog;
import com.novel.script.repository.VideoScriptEpisodeRepository;
import com.novel.script.repository.VideoScriptLogRepository;
import com.novel.script.repository.VideoScriptRepository;
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
 * 剧本工作流引擎（短视频多集版本）
 *
 * 参考短篇小说工厂：
 * 1) 系列设定（Story Bible）
 * 2) 多阶段大纲（多集走向）
 * 3) 看点/标题/核心 + 初始化每集元数据
 * 4) 导语（黄金开头）
 * 5) 循环生成每集：生成 -> 审稿 -> 分析 -> 决策（可更新大纲/后续看点）-> 封装入库
 */
@Service
public class VideoScriptWorkflowEngine {

    private static final Logger logger = LoggerFactory.getLogger(VideoScriptWorkflowEngine.class);

    @Autowired
    private VideoScriptRepository scriptRepository;

    @Autowired
    private VideoScriptEpisodeRepository episodeRepository;

    @Autowired
    private VideoScriptLogRepository logRepository;

    @Autowired
    private VideoScriptAIService aiService;

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void execute(Long scriptId) {
        logger.info("🚀 开始执行多集剧本工作流: scriptId={}", scriptId);

        VideoScript script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new RuntimeException("剧本不存在"));

        try {
            // 读取工作流配置中的模型ID
            String modelId = extractModelIdFromConfig(script.getWorkflowConfig());
            if (modelId != null) {
                aiService.setCurrentModelId(modelId);
                addLog(scriptId, null, "INFO", "使用模型: " + modelId);
            }

            int seconds = script.getTargetSeconds() != null ? script.getTargetSeconds() : 60;
            int scenes = computeSceneCount(seconds, script.getSceneCount());
            if (script.getSceneCount() == null || script.getSceneCount() <= 0) {
                script.setSceneCount(scenes);
            }

            if (script.getEpisodeCount() == null || script.getEpisodeCount() <= 0) {
                script.setEpisodeCount(10);
            }
            if (script.getEnableOutlineUpdate() == null) {
                script.setEnableOutlineUpdate(true);
            }
            if (script.getMinPassScore() == null) {
                script.setMinPassScore(7);
            }
            if (script.getMaxRetryPerEpisode() == null || script.getMaxRetryPerEpisode() <= 0) {
                script.setMaxRetryPerEpisode(3);
            }
            scriptRepository.save(script);

            if (Thread.currentThread().isInterrupted()) {
                logger.info("工作流被中断(开始前): scriptId={}", scriptId);
                return;
            }

            // 0) 系列设定
            if (isBlank(script.getScriptSetting())) {
                generateStorySetting(script, seconds, scenes);
            }

            // 1) 大纲
            if (isBlank(script.getOutline())) {
                generateOutline(script);
            }

            // 2) 看点+初始化 episodes
            List<VideoScriptEpisode> episodes = episodeRepository.findByScriptIdOrderByEpisodeNumberAsc(scriptId);
            if (episodes.isEmpty()) {
                generateHooksAndInitEpisodes(script);
                episodes = episodeRepository.findByScriptIdOrderByEpisodeNumberAsc(scriptId);
            }

            // 3) 导语
            if (isBlank(script.getPrologue())) {
                generatePrologue(script, episodes);
            }

            // 4) 循环生成每集
            int episodeCount = script.getEpisodeCount() != null ? script.getEpisodeCount() : 0;
            int current = script.getCurrentEpisode() != null ? script.getCurrentEpisode() : 0;
            for (int i = current + 1; i <= episodeCount; i++) {
                if (Thread.currentThread().isInterrupted()) {
                    logger.info("工作流被中断: scriptId={}", scriptId);
                    return;
                }
                generateSingleEpisode(script, i);
            }

            // 完成
            script.setActiveStep("COMPLETED");
            script.setStatus("COMPLETED");
            scriptRepository.save(script);

            addLog(scriptId, null, "SUCCESS", "🎉 全部剧集生成完成！");
            sendProgress(scriptId, 100, "COMPLETED", "工作流完成");

            logger.info("✅ 多集剧本工作流执行完成: scriptId={}", scriptId);

        } catch (Exception e) {
            logger.error("❌ 多集剧本工作流执行失败: scriptId={}", scriptId, e);

            script.setActiveStep("FAILED");
            script.setStatus("FAILED");
            script.setErrorMessage(e.getMessage());
            scriptRepository.save(script);

            addLog(scriptId, null, "ERROR", "工作流失败: " + e.getMessage());
            sendProgress(scriptId, -1, "FAILED", e.getMessage());
        } finally {
            aiService.clearCurrentModelId();
        }
    }

    private int computeSceneCount(int seconds, Integer sceneCount) {
        if (sceneCount != null && sceneCount > 0) {
            return sceneCount;
        }
        // 默认 3 秒一个镜头
        return Math.max(10, (int) Math.ceil(seconds / 3.0));
    }

    /**
     * 从 workflowConfig JSON 中提取 modelId
     */
    private String extractModelIdFromConfig(String workflowConfig) {
        if (workflowConfig == null || workflowConfig.trim().isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> config = objectMapper.readValue(workflowConfig, new TypeReference<Map<String, Object>>() {
            });
            Object modelId = config.get("modelId");
            return modelId != null ? modelId.toString() : null;
        } catch (Exception e) {
            logger.warn("解析 workflowConfig 失败: {}", e.getMessage());
            return null;
        }
    }

    @Transactional
    protected void generateStorySetting(VideoScript script, int seconds, int scenes) {
        script.setActiveStep("STORY_SETTING");
        script.setStatus("WORKFLOW_RUNNING");
        scriptRepository.save(script);

        addLog(script.getId(), null, "INFO", "🧱 开始生成系列设定（Story Bible）...");
        sendProgress(script.getId(), 3, "WORKFLOW_RUNNING", "AI正在生成系列设定");

        String storySetting = aiService.generateStorySetting(
                script.getTitle(),
                script.getIdea(),
                script.getMode(),
                seconds,
                scenes,
                script.getEpisodeCount() != null ? script.getEpisodeCount() : 10,
                script.getUserId()
        );

        script.setScriptSetting(storySetting);
        scriptRepository.save(script);

        addLog(script.getId(), null, "SUCCESS", "✅ 系列设定生成完成");
        sendProgress(script.getId(), 6, "WORKFLOW_RUNNING", "系列设定生成完成");
    }

    @Transactional
    protected void generateOutline(VideoScript script) {
        script.setActiveStep("OUTLINE");
        script.setStatus("GENERATING_OUTLINE");
        scriptRepository.save(script);

        addLog(script.getId(), null, "INFO", "📝 开始生成系列大纲...");
        sendProgress(script.getId(), 8, "GENERATING_OUTLINE", "AI正在生成大纲");

        String outline = aiService.generateOutline(
                script.getIdea() != null ? script.getIdea() : "",
                script.getScriptSetting() != null ? script.getScriptSetting() : "",
                script.getEpisodeCount() != null ? script.getEpisodeCount() : 10,
                script.getUserId()
        );

        script.setOutline(outline);
        script.setStatus("WORKFLOW_RUNNING");
        scriptRepository.save(script);

        addLog(script.getId(), null, "SUCCESS", "✅ 大纲生成完成");
        sendProgress(script.getId(), 12, "WORKFLOW_RUNNING", "大纲生成完成");
    }

    @Transactional
    protected void generateHooksAndInitEpisodes(VideoScript script) {
        script.setActiveStep("HOOKS");
        script.setStatus("WORKFLOW_RUNNING");
        scriptRepository.save(script);

        addLog(script.getId(), null, "INFO", "✨ 开始生成每集看点（标题+核心+悬念）...");
        sendProgress(script.getId(), 16, "WORKFLOW_RUNNING", "正在生成每集看点");

        int episodeCount = script.getEpisodeCount() != null ? script.getEpisodeCount() : 10;
        try {
            List<Map<String, Object>> hooks = aiService.generateHooks(
                    script.getScriptSetting() != null ? script.getScriptSetting() : "",
                    script.getOutline() != null ? script.getOutline() : "",
                    episodeCount,
                    script.getUserId()
            );

            try {
                script.setHooksJson(objectMapper.writeValueAsString(hooks));
            } catch (Exception e) {
                logger.warn("hooks_json 序列化失败: {}", e.getMessage());
                script.setHooksJson(null);
            }
            scriptRepository.save(script);

            Map<Integer, Map<String, Object>> hookByEpisode = new HashMap<>();
            for (Map<String, Object> h : hooks) {
                Integer num = parseEpisodeNumber(h.get("episodeNumber"));
                if (num != null) {
                    hookByEpisode.put(num, h);
                }
            }

            for (int i = 1; i <= episodeCount; i++) {
                Map<String, Object> hook = hookByEpisode.get(i);
                String title = hook != null ? String.valueOf(hook.getOrDefault("title", "第" + i + "集")) : ("第" + i + "集");
                String core = hook != null ? String.valueOf(hook.getOrDefault("core", "")) : "";

                VideoScriptEpisode ep = new VideoScriptEpisode();
                ep.setScriptId(script.getId());
                ep.setEpisodeNumber(i);
                ep.setTitle(title);
                ep.setBrief(core);
                ep.setStatus("PENDING");
                episodeRepository.save(ep);
            }

            addLog(script.getId(), null, "SUCCESS", "✅ 看点生成完成，剧集已初始化");
            sendProgress(script.getId(), 20, "WORKFLOW_RUNNING", "剧集看点生成完成");
        } catch (Exception e) {
            addLog(script.getId(), null, "ERROR", "看点生成失败，回退为默认剧集初始化: " + e.getMessage());
            for (int i = 1; i <= episodeCount; i++) {
                VideoScriptEpisode ep = new VideoScriptEpisode();
                ep.setScriptId(script.getId());
                ep.setEpisodeNumber(i);
                ep.setTitle("第" + i + "集");
                ep.setBrief("");
                ep.setStatus("PENDING");
                episodeRepository.save(ep);
            }
        }
    }

    @Transactional
    protected void generatePrologue(VideoScript script, List<VideoScriptEpisode> episodes) {
        script.setActiveStep("PROLOGUE");
        script.setStatus("WORKFLOW_RUNNING");
        scriptRepository.save(script);

        addLog(script.getId(), null, "INFO", "✨ 开始生成导语（黄金开头）...");
        sendProgress(script.getId(), 22, "WORKFLOW_RUNNING", "正在生成导语");

        String firstHook = "";
        if (episodes != null && !episodes.isEmpty()) {
            VideoScriptEpisode first = episodes.get(0);
            firstHook = (first.getTitle() != null ? first.getTitle() : "") + "：" + (first.getBrief() != null ? first.getBrief() : "");
        }

        String prologue = aiService.generatePrologue(
                script.getTitle(),
                script.getIdea(),
                script.getScriptSetting() != null ? script.getScriptSetting() : "",
                script.getOutline() != null ? script.getOutline() : "",
                firstHook,
                script.getUserId()
        );

        script.setPrologue(prologue);
        scriptRepository.save(script);

        addLog(script.getId(), null, "SUCCESS", "✅ 导语生成完成");
        sendProgress(script.getId(), 25, "WORKFLOW_RUNNING", "导语生成完成");
    }

    @Transactional
    protected void generateSingleEpisode(VideoScript script, int episodeNumber) {
        logger.info("开始生成剧集: scriptId={}, episode={}", script.getId(), episodeNumber);

        VideoScriptEpisode episode = episodeRepository.findByScriptIdAndEpisodeNumber(script.getId(), episodeNumber)
                .orElseThrow(() -> new RuntimeException("剧集不存在: " + episodeNumber));

        int episodeCount = script.getEpisodeCount() != null ? script.getEpisodeCount() : 0;
        int baseProgress = 25 + (int) (((episodeNumber - 1) / (double) Math.max(1, episodeCount)) * 70);

        String previousContent = buildPreviousContent(script.getId(), episodeNumber);
        String adjustment = null;

        for (int retry = 0; retry < (script.getMaxRetryPerEpisode() != null ? script.getMaxRetryPerEpisode() : 3); retry++) {
            if (Thread.currentThread().isInterrupted()) {
                logger.info("工作流被中断: scriptId={}, episode={}", script.getId(), episodeNumber);
                return;
            }

            // 生成阶段
            script.setActiveStep("EPISODE_" + episodeNumber + "_GENERATE");
            script.setCurrentRetryCount(retry);
            script.setStatus("WORKFLOW_RUNNING");
            scriptRepository.save(script);

            episode.setStatus(retry == 0 ? "GENERATING" : "REVISING");
            episode.setLastAdjustment(adjustment);
            episodeRepository.save(episode);

            addLog(script.getId(), episodeNumber, "ACTION",
                    retry == 0 ? "🖋️ 生成第" + episodeNumber + "集" : "🔄 重写第" + episodeNumber + "集（第" + (retry + 1) + "次）");
            sendProgress(script.getId(), baseProgress + 3, "WORKFLOW_RUNNING", "生成第" + episodeNumber + "集");

            long startTime = System.currentTimeMillis();
            String outlineForPrompt = buildOutlineForPrompt(script);
            String episodeBriefForPrompt = (episode.getTitle() != null ? episode.getTitle() : "")
                    + "：" + (episode.getBrief() != null ? episode.getBrief() : "");
            String content = aiService.generateEpisode(
                    script.getMode(),
                    script.getScriptFormat(),
                    outlineForPrompt,
                    previousContent,
                    episodeBriefForPrompt,
                    script.getTargetSeconds() != null ? script.getTargetSeconds() : 60,
                    script.getSceneCount() != null ? script.getSceneCount() : 20,
                    adjustment,
                    script.getUserId()
            );
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;

            episode.setContent(content);
            episode.setWordCount(content != null ? content.length() : 0);
            episode.setGenerationTime(elapsed);
            episode.setStatus("REVIEWING");
            episodeRepository.save(episode);

            // 审稿阶段
            script.setActiveStep("EPISODE_" + episodeNumber + "_REVIEW");
            scriptRepository.save(script);

            addLog(script.getId(), episodeNumber, "ACTION", "🔍 AI审稿中...");
            sendProgress(script.getId(), baseProgress + 6, "WORKFLOW_RUNNING", "AI审稿第" + episodeNumber + "集");

            Map<String, Object> review = aiService.reviewEpisode(content, episode.getBrief(), script.getUserId());
            try {
                episode.setReviewResult(objectMapper.writeValueAsString(review));
            } catch (Exception e) {
                logger.warn("reviewResult JSON serialization failed: {}", e.getMessage());
                episode.setReviewResult("{}");
            }
            episodeRepository.save(episode);

            int score = review.get("score") instanceof Number ? ((Number) review.get("score")).intValue() : 0;
            boolean passed = score >= (script.getMinPassScore() != null ? script.getMinPassScore() : 7);

            addLog(script.getId(), episodeNumber, "REVIEW",
                    String.format("📊 审稿结果: 得分 %d/10 %s", score, passed ? "✅通过" : "❌未通过"));

            if (!passed) {
                String comments = String.valueOf(review.getOrDefault("comments", ""));
                String suggestions = String.valueOf(review.getOrDefault("suggestions", ""));
                adjustment = "请根据以下审稿建议对本集进行重写（保持设定/大纲/前文事实一致，不要跑偏）：\n"
                        + "- 评价：" + comments + "\n"
                        + "- 建议：" + suggestions + "\n"
                        + "要求：短平快、全程高能、画面具体可拍、每3-5秒一个钩子/反转、衔接更顺。";
                addLog(script.getId(), episodeNumber, "THOUGHT", "♻️ 审稿未通过，已生成返工指令，将重新生成本集。");
                continue;
            }

            // 通过：本集完成
            episode.setStatus("COMPLETED");
            episodeRepository.save(episode);

            // 分析阶段
            script.setActiveStep("EPISODE_" + episodeNumber + "_ANALYZE");
            scriptRepository.save(script);

            addLog(script.getId(), episodeNumber, "THOUGHT", "🧠 连续性分析 & 后续推演...");
            sendProgress(script.getId(), baseProgress + 9, "WORKFLOW_RUNNING", "分析第" + episodeNumber + "集与后续");

            try {
                Map<String, Object> analysis = aiService.analyzeEpisode(
                        script.getScriptSetting() != null ? script.getScriptSetting() : "",
                        script.getOutline() != null ? script.getOutline() : "",
                        episodeNumber,
                        episodeCount,
                        episode.getTitle(),
                        episode.getBrief(),
                        content != null ? content : "",
                        script.getUserId()
                );

                try {
                    episode.setAnalysisResult(objectMapper.writeValueAsString(analysis));
                } catch (Exception e) {
                    logger.warn("analysisResult JSON serialization failed: {}", e.getMessage());
                }
                episodeRepository.save(episode);

                // 决策阶段：是否更新大纲/后续看点
                script.setActiveStep("EPISODE_" + episodeNumber + "_DECIDE");
                scriptRepository.save(script);

                boolean needOutlineUpdate = Boolean.TRUE.equals(analysis.get("needOutlineUpdate"));
                boolean needHookUpdate = Boolean.TRUE.equals(analysis.get("needHookUpdate"));

                if (Boolean.TRUE.equals(script.getEnableOutlineUpdate()) && needOutlineUpdate) {
                    addLog(script.getId(), episodeNumber, "ACTION", "📝 根据分析调整大纲...");
                    String outlineSuggestion = String.valueOf(analysis.getOrDefault("outlineUpdateSuggestion", ""));
                    String updateContext = "【本集内容】\n" + (content != null ? content : "")
                            + "\n\n【调整建议】\n" + outlineSuggestion;

                    String updatedOutline = aiService.updateOutline(
                            script.getOutline() != null ? script.getOutline() : "",
                            updateContext,
                            episodeNumber,
                            script.getUserId()
                    );
                    script.setOutline(updatedOutline);
                    scriptRepository.save(script);
                    addLog(script.getId(), episodeNumber, "SUCCESS", "✅ 大纲已更新");
                }

                if (needHookUpdate && episodeNumber < episodeCount) {
                    String guidance = String.valueOf(analysis.getOrDefault("hookUpdateGuidance", ""));
                    updateFutureHooks(script, episodeNumber + 1, guidance);
                }

            } catch (Exception e) {
                logger.warn("剧集分析/决策阶段失败: {}", e.getMessage());
                addLog(script.getId(), episodeNumber, "ERROR", "剧集分析/决策阶段失败: " + e.getMessage());
            }

            // 封装阶段：推进游标
            script.setActiveStep("EPISODE_" + episodeNumber + "_COMMIT");
            script.setCurrentEpisode(episodeNumber);
            scriptRepository.save(script);

            addLog(script.getId(), episodeNumber, "SUCCESS", "✅ 第" + episodeNumber + "集完成（已封装并准备进入下一集）");
            sendProgress(script.getId(), baseProgress + 12, "WORKFLOW_RUNNING", "第" + episodeNumber + "集完成");
            return;
        }

        episode.setStatus("FAILED");
        episodeRepository.save(episode);
        script.setActiveStep("EPISODE_" + episodeNumber + "_FAILED");
        scriptRepository.save(script);

        throw new RuntimeException("第" + episodeNumber + "集生成失败，已达最大重试次数");
    }

    /**
     * 构建用于下一集生成的“连续性记忆”（优先使用 analysisResult 中的 episodeSummary）
     */
    private String buildPreviousContent(Long scriptId, int currentEpisode) {
        if (currentEpisode <= 1) {
            return "";
        }

        List<VideoScriptEpisode> previous = episodeRepository.findByScriptIdOrderByEpisodeNumberAsc(scriptId)
                .stream()
                .filter(e -> e.getEpisodeNumber() != null
                        && e.getEpisodeNumber() < currentEpisode
                        && "COMPLETED".equals(e.getStatus()))
                .toList();

        if (previous.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【连续性记忆（已完成剧情摘要）】\n");
        for (VideoScriptEpisode ep : previous) {
            sb.append("【第").append(ep.getEpisodeNumber()).append("集 ").append(ep.getTitle() != null ? ep.getTitle() : "").append("】\n");

            String summary = extractEpisodeSummary(ep.getAnalysisResult());
            if (!isBlank(summary)) {
                sb.append(summary);
            } else if (!isBlank(ep.getContent())) {
                sb.append(truncate(ep.getContent(), 1200));
            }
            sb.append("\n\n");
        }
        return sb.toString().trim();
    }

    private String extractEpisodeSummary(String analysisResultJson) {
        if (isBlank(analysisResultJson)) {
            return "";
        }
        try {
            Map<String, Object> m = objectMapper.readValue(analysisResultJson, new TypeReference<Map<String, Object>>() {
            });
            Object s = m.get("episodeSummary");
            return s != null ? String.valueOf(s) : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, Math.max(0, maxLen)) + "...";
    }

    /**
     * 构建用于生成的“设定 + 导语 + 大纲”上下文
     */
    private String buildOutlineForPrompt(VideoScript script) {
        StringBuilder sb = new StringBuilder();
        if (!isBlank(script.getScriptSetting())) {
            sb.append("【系列设定】\n").append(script.getScriptSetting().trim()).append("\n\n");
        }
        if (!isBlank(script.getPrologue())) {
            sb.append("【导语】\n").append(script.getPrologue().trim()).append("\n\n");
        }
        if (!isBlank(script.getOutline())) {
            sb.append("【系列大纲】\n").append(script.getOutline());
        }
        return sb.toString().trim();
    }

    /**
     * 更新后续剧集的看点（标题/核心），并同步到 episodes 与 hooks_json
     */
    @Transactional
    protected void updateFutureHooks(VideoScript script, int fromEpisode, String guidance) {
        int episodeCount = script.getEpisodeCount() != null ? script.getEpisodeCount() : 0;
        if (fromEpisode > episodeCount) {
            return;
        }

        addLog(script.getId(), fromEpisode, "ACTION", "🧩 更新后续剧集看点（标题/核心）...");

        try {
            String currentHooksJson = buildRemainingHooksJson(script, fromEpisode);
            List<Map<String, Object>> updated = aiService.updateHooks(
                    fromEpisode,
                    episodeCount,
                    guidance != null ? guidance : "",
                    currentHooksJson,
                    script.getUserId()
            );

            applyHooksToEpisodes(script.getId(), updated);
            mergeAndSaveHooksJson(script, updated);

            addLog(script.getId(), fromEpisode, "SUCCESS", "✅ 后续看点已更新");
        } catch (Exception e) {
            logger.warn("后续看点更新失败: {}", e.getMessage());
            addLog(script.getId(), fromEpisode, "ERROR", "后续看点更新失败: " + e.getMessage());
        }
    }

    private String buildRemainingHooksJson(VideoScript script, int fromEpisode) {
        try {
            if (!isBlank(script.getHooksJson())) {
                List<Map<String, Object>> all = objectMapper.readValue(
                        script.getHooksJson(),
                        new TypeReference<List<Map<String, Object>>>() {
                        }
                );
                List<Map<String, Object>> filtered = all.stream()
                        .filter(m -> {
                            Integer num = parseEpisodeNumber(m.get("episodeNumber"));
                            return num != null && num >= fromEpisode;
                        })
                        .toList();
                return objectMapper.writeValueAsString(filtered);
            }
        } catch (Exception ignored) {
        }

        List<Map<String, Object>> minimal = episodeRepository.findByScriptIdOrderByEpisodeNumberAsc(script.getId())
                .stream()
                .filter(e -> e.getEpisodeNumber() != null && e.getEpisodeNumber() >= fromEpisode)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("episodeNumber", e.getEpisodeNumber());
                    m.put("title", e.getTitle());
                    m.put("core", e.getBrief());
                    return m;
                })
                .toList();

        try {
            return objectMapper.writeValueAsString(minimal);
        } catch (Exception e) {
            return "[]";
        }
    }

    private void applyHooksToEpisodes(Long scriptId, List<Map<String, Object>> hooks) {
        for (Map<String, Object> hook : hooks) {
            Integer num = parseEpisodeNumber(hook.get("episodeNumber"));
            if (num == null) {
                continue;
            }

            VideoScriptEpisode ep = episodeRepository.findByScriptIdAndEpisodeNumber(scriptId, num).orElse(null);
            if (ep == null) {
                continue;
            }

            if ("COMPLETED".equals(ep.getStatus())) {
                continue;
            }
            if (!isBlank(ep.getContent())) {
                continue;
            }

            ep.setTitle(String.valueOf(hook.getOrDefault("title", ep.getTitle())));
            ep.setBrief(String.valueOf(hook.getOrDefault("core", ep.getBrief())));
            episodeRepository.save(ep);
        }
    }

    private void mergeAndSaveHooksJson(VideoScript script, List<Map<String, Object>> updatedHooks) {
        try {
            List<Map<String, Object>> merged;
            if (!isBlank(script.getHooksJson())) {
                merged = objectMapper.readValue(
                        script.getHooksJson(),
                        new TypeReference<List<Map<String, Object>>>() {
                        }
                );
            } else {
                merged = new java.util.ArrayList<>();
            }

            Map<Integer, Map<String, Object>> updateMap = new HashMap<>();
            for (Map<String, Object> h : updatedHooks) {
                Integer num = parseEpisodeNumber(h.get("episodeNumber"));
                if (num != null) {
                    updateMap.put(num, h);
                }
            }

            java.util.List<Map<String, Object>> result = new java.util.ArrayList<>();
            for (Map<String, Object> h : merged) {
                Integer num = parseEpisodeNumber(h.get("episodeNumber"));
                if (num != null && updateMap.containsKey(num)) {
                    result.add(updateMap.remove(num));
                } else {
                    result.add(h);
                }
            }
            result.addAll(updateMap.values());

            result.sort((a, b) -> {
                Integer na = parseEpisodeNumber(a.get("episodeNumber"));
                Integer nb = parseEpisodeNumber(b.get("episodeNumber"));
                return Integer.compare(na != null ? na : 0, nb != null ? nb : 0);
            });

            script.setHooksJson(objectMapper.writeValueAsString(result));
            scriptRepository.save(script);
        } catch (Exception e) {
            logger.warn("合并 hooks_json 失败: {}", e.getMessage());
            try {
                script.setHooksJson(objectMapper.writeValueAsString(updatedHooks));
                scriptRepository.save(script);
            } catch (Exception ignored) {
            }
        }
    }

    private Integer parseEpisodeNumber(Object obj) {
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

    private void addLog(Long scriptId, Integer episodeNumber, String type, String content) {
        VideoScriptLog log = new VideoScriptLog();
        log.setScriptId(scriptId);
        log.setEpisodeNumber(episodeNumber);
        log.setType(type);
        log.setContent(content);
        logRepository.save(log);

        if (messagingTemplate != null) {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", type);
            msg.put("content", content);
            msg.put("episodeNumber", episodeNumber);
            msg.put("timestamp", LocalDateTime.now());
            messagingTemplate.convertAndSend("/topic/video-script/" + scriptId + "/logs", msg);
        }

        logger.info("[剧本工作流日志] scriptId={}, episode={}, type={}, content={}", scriptId, episodeNumber, type, content);
    }

    private void sendProgress(Long scriptId, int percentage, String status, String message) {
        if (messagingTemplate != null) {
            Map<String, Object> progress = new HashMap<>();
            progress.put("percentage", percentage);
            progress.put("status", status);
            progress.put("message", message);
            progress.put("timestamp", LocalDateTime.now());
            messagingTemplate.convertAndSend("/topic/video-script/" + scriptId + "/progress", progress);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
