package com.novel.script.service;

import com.novel.script.dto.VideoScriptWorkflowStateResponse;
import com.novel.script.entity.VideoScript;
import com.novel.script.entity.VideoScriptEpisode;
import com.novel.script.entity.VideoScriptLog;
import com.novel.script.repository.VideoScriptEpisodeRepository;
import com.novel.script.repository.VideoScriptLogRepository;
import com.novel.script.repository.VideoScriptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VideoScriptServiceImpl implements VideoScriptService {

    private static final Logger logger = LoggerFactory.getLogger(VideoScriptServiceImpl.class);

    @Autowired
    private VideoScriptRepository scriptRepository;

    @Autowired
    private VideoScriptLogRepository logRepository;

    @Autowired
    private VideoScriptEpisodeRepository episodeRepository;

    @Autowired
    private VideoScriptWorkflowEngine workflowEngine;

    // 存储正在运行的工作流任务（scriptId -> Thread）
    private final ConcurrentHashMap<Long, Thread> runningWorkflows = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public VideoScript createScript(VideoScript script) {
        return scriptRepository.save(script);
    }

    @Override
    public VideoScript getScript(Long id) {
        return scriptRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("剧本不存在"));
    }

    @Override
    public List<VideoScript> getUserScripts(Long userId) {
        return scriptRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    @Override
    @Transactional
    public void deleteScript(Long id) {
        pauseWorkflow(id);
        episodeRepository.deleteByScriptId(id);
        logRepository.deleteByScriptId(id);
        scriptRepository.deleteById(id);
    }

    @Override
    public List<VideoScriptEpisode> getEpisodes(Long scriptId) {
        return episodeRepository.findByScriptIdOrderByEpisodeNumberAsc(scriptId);
    }

    @Override
    public VideoScriptEpisode getEpisode(Long scriptId, Integer episodeNumber) {
        return episodeRepository.findByScriptIdAndEpisodeNumber(scriptId, episodeNumber)
                .orElseThrow(() -> new RuntimeException("剧集不存在"));
    }

    @Override
    @Transactional
    public VideoScriptEpisode updateEpisodeContent(Long scriptId, Integer episodeNumber, String content) {
        VideoScriptEpisode ep = getEpisode(scriptId, episodeNumber);
        ep.setContent(content);
        ep.setWordCount(content != null ? content.length() : 0);
        return episodeRepository.save(ep);
    }

    @Override
    public void startWorkflow(Long scriptId) {
        VideoScript script = getScript(scriptId);

        if (runningWorkflows.containsKey(scriptId)) {
            throw new RuntimeException("工作流已在运行中");
        }

        if ("COMPLETED".equals(script.getStatus())) {
            throw new RuntimeException("剧本已完成，无需再次生成");
        }

        script.setStatus("WORKFLOW_RUNNING");
        scriptRepository.save(script);

        Thread workflowThread = new Thread(() -> {
            try {
                workflowEngine.execute(scriptId);
            } catch (Exception e) {
                logger.error("剧本工作流执行失败: scriptId={}", scriptId, e);

                VideoScript failed = scriptRepository.findById(scriptId).orElse(null);
                if (failed != null) {
                    failed.setStatus("FAILED");
                    failed.setActiveStep("FAILED");
                    failed.setErrorMessage(e.getMessage());
                    scriptRepository.save(failed);
                }
            } finally {
                runningWorkflows.remove(scriptId);
            }
        }, "video-script-workflow-" + scriptId);

        runningWorkflows.put(scriptId, workflowThread);
        workflowThread.start();

        logger.info("剧本工作流已启动: scriptId={}", scriptId);
    }

    @Override
    @Transactional
    public void pauseWorkflow(Long scriptId) {
        Thread t = runningWorkflows.remove(scriptId);
        if (t != null && t.isAlive()) {
            t.interrupt();
            logger.info("剧本工作流已暂停: scriptId={}", scriptId);
        }

        VideoScript script = scriptRepository.findById(scriptId).orElse(null);
        if (script != null && ("WORKFLOW_RUNNING".equals(script.getStatus()) || "GENERATING_OUTLINE".equals(script.getStatus()))) {
            script.setStatus("WORKFLOW_PAUSED");
            scriptRepository.save(script);
        }
    }

    @Override
    @Transactional
    public void retryEpisode(Long scriptId, Integer episodeNumber) {
        VideoScript script = getScript(scriptId);

        // 重置 episode
        VideoScriptEpisode ep = episodeRepository.findByScriptIdAndEpisodeNumber(scriptId, episodeNumber)
                .orElseThrow(() -> new RuntimeException("剧集不存在"));

        ep.setStatus("PENDING");
        ep.setContent(null);
        ep.setStoryboard(null);
        ep.setReviewResult(null);
        ep.setAnalysisResult(null);
        ep.setLastAdjustment(null);
        ep.setWordCount(0);
        ep.setGenerationTime(null);
        episodeRepository.save(ep);

        // 重置 series 工作流游标
        script.setCurrentEpisode(episodeNumber - 1);
        script.setCurrentRetryCount(0);
        script.setActiveStep("EPISODE_" + episodeNumber + "_GENERATE");
        script.setStatus("WORKFLOW_PAUSED");
        scriptRepository.save(script);

        logger.info("剧集已重置: scriptId={}, episodeNumber={}", scriptId, episodeNumber);
    }

    @Override
    public List<VideoScriptLog> getLogs(Long scriptId, Integer episodeNumber, Pageable pageable) {
        if (episodeNumber != null) {
            return logRepository.findByScriptIdAndEpisodeNumberOrderByCreatedAtDesc(scriptId, episodeNumber, pageable);
        }
        return logRepository.findByScriptIdOrderByCreatedAtDesc(scriptId, pageable);
    }

    @Override
    public VideoScriptWorkflowStateResponse getWorkflowState(Long scriptId) {
        VideoScript script = getScript(scriptId);
        List<VideoScriptEpisode> episodes = getEpisodes(scriptId);

        VideoScriptWorkflowStateResponse resp = new VideoScriptWorkflowStateResponse();
        resp.setScriptId(scriptId);
        resp.setStatus(script.getStatus());
        resp.setActiveStep(script.getActiveStep());
        resp.setEpisodeCount(script.getEpisodeCount());
        resp.setCurrentEpisode(script.getCurrentEpisode());

        String active = script.getActiveStep();

        // 全局步骤
        resp.getSteps().add(new VideoScriptWorkflowStateResponse.WorkflowStep(
                "STORY_SETTING",
                "系列设定",
                stepStatus(active, "STORY_SETTING", isNotBlank(script.getScriptSetting()), false),
                null,
                "生成世界观/人物/风格/连续性规则"
        ));

        resp.getSteps().add(new VideoScriptWorkflowStateResponse.WorkflowStep(
                "OUTLINE",
                "生成系列大纲",
                stepStatus(active, "OUTLINE", isNotBlank(script.getOutline()), false),
                null,
                "生成多阶段、多集走向的大纲"
        ));

        resp.getSteps().add(new VideoScriptWorkflowStateResponse.WorkflowStep(
                "HOOKS",
                "生成每集看点",
                stepStatus(active, "HOOKS", !episodes.isEmpty() || isNotBlank(script.getHooksJson()), false),
                null,
                "为每集生成标题+一句话核心+悬念"
        ));

        resp.getSteps().add(new VideoScriptWorkflowStateResponse.WorkflowStep(
                "PROLOGUE",
                "生成导语",
                stepStatus(active, "PROLOGUE", isNotBlank(script.getPrologue()), false),
                null,
                "生成黄金开头/开场钩子"
        ));

        int count = script.getEpisodeCount() != null ? script.getEpisodeCount() : 0;
        for (int i = 1; i <= count; i++) {
            final int epNum = i;
            VideoScriptEpisode ep = episodes.stream()
                    .filter(e -> e.getEpisodeNumber() != null && e.getEpisodeNumber() == epNum)
                    .findFirst()
                    .orElse(null);

            boolean failed = ep != null && "FAILED".equals(ep.getStatus());
            boolean contentReady = ep != null && isNotBlank(ep.getContent());
            boolean reviewReady = ep != null && isNotBlank(ep.getReviewResult());
            boolean analysisReady = ep != null && isNotBlank(ep.getAnalysisResult());
            boolean committed = ep != null && "COMPLETED".equals(ep.getStatus());

            resp.getSteps().add(new VideoScriptWorkflowStateResponse.WorkflowStep(
                    "EPISODE_" + i + "_GENERATE",
                    "生成第" + i + "集",
                    stepStatus(active, "EPISODE_" + i + "_GENERATE", contentReady, failed),
                    i,
                    "根据看点核心生成本集脚本"
            ));
            resp.getSteps().add(new VideoScriptWorkflowStateResponse.WorkflowStep(
                    "EPISODE_" + i + "_REVIEW",
                    "审核第" + i + "集",
                    stepStatus(active, "EPISODE_" + i + "_REVIEW", reviewReady, failed),
                    i,
                    "AI审稿打分，未通过则返工重写"
            ));
            resp.getSteps().add(new VideoScriptWorkflowStateResponse.WorkflowStep(
                    "EPISODE_" + i + "_ANALYZE",
                    "连续性分析",
                    stepStatus(active, "EPISODE_" + i + "_ANALYZE", analysisReady, failed),
                    i,
                    "分析连续性/风险/后续建议"
            ));
            resp.getSteps().add(new VideoScriptWorkflowStateResponse.WorkflowStep(
                    "EPISODE_" + i + "_DECIDE",
                    "调整决策",
                    stepStatus(active, "EPISODE_" + i + "_DECIDE", analysisReady, failed),
                    i,
                    "判断是否更新大纲或后续看点"
            ));
            resp.getSteps().add(new VideoScriptWorkflowStateResponse.WorkflowStep(
                    "EPISODE_" + i + "_COMMIT",
                    "封装入库",
                    stepStatus(active, "EPISODE_" + i + "_COMMIT", committed, failed),
                    i,
                    "保存本集产物，准备进入下一集"
            ));
        }

        return resp;
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String stepStatus(String activeStep, String key, boolean completed, boolean failed) {
        if (activeStep != null && activeStep.equals(key)) {
            return "RUNNING";
        }
        if (failed) {
            return "FAILED";
        }
        if (completed) {
            return "COMPLETED";
        }
        return "PENDING";
    }

    @Override
    @Transactional
    public void updateConfig(Long scriptId, Map<String, String> config) {
        VideoScript script = getScript(scriptId);

        if (!"DRAFT".equals(script.getStatus()) && !"WORKFLOW_PAUSED".equals(script.getStatus())) {
            throw new RuntimeException("只能在未启动或已暂停状态下修改配置");
        }

        boolean changed = false;

        // 模型配置（写入 workflowConfig）
        String modelId = config.get("modelId");
        if (modelId != null && !modelId.trim().isEmpty()) {
            script.setWorkflowConfig("{\"modelId\":\"" + modelId.trim() + "\"}");
            changed = true;
            logger.info("已更新剧本工作流配置: scriptId={}, modelId={}", scriptId, modelId);
        }

        // 剧本格式（写入 video_scripts.script_format）
        String fmt = config.get("scriptFormat");
        if (fmt != null && !fmt.trim().isEmpty()) {
            String v = fmt.trim().toUpperCase();
            if (!"SCENE".equals(v) && !"STORYBOARD".equals(v) && !"NARRATION".equals(v)) {
                v = "STORYBOARD";
            }
            script.setScriptFormat(v);
            changed = true;
            logger.info("已更新剧本格式: scriptId={}, scriptFormat={}", scriptId, v);
        }

        if (changed) {
            scriptRepository.save(script);
        }
    }
}
