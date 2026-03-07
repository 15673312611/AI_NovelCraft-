package com.novel.script.controller;

import com.novel.common.security.AuthUtils;
import com.novel.script.dto.CreateVideoScriptRequest;
import com.novel.script.dto.VideoScriptWorkflowStateResponse;
import com.novel.script.entity.VideoScript;
import com.novel.script.entity.VideoScriptEpisode;
import com.novel.script.entity.VideoScriptLog;
import com.novel.script.service.VideoScriptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/video-scripts")
public class VideoScriptController {

    @Autowired
    private VideoScriptService videoScriptService;

    @PostMapping
    public VideoScript create(@RequestBody CreateVideoScriptRequest request) {
        Long userId = AuthUtils.getCurrentUserId();

        VideoScript script = new VideoScript();
        script.setUserId(userId);
        script.setTitle(request.getTitle());
        script.setIdea(request.getIdea());

        // 默认 60s
        int seconds = request.getTargetSeconds() != null ? request.getTargetSeconds() : 60;
        script.setTargetSeconds(seconds);

        // 模式
        String mode = request.getMode();
        if (mode != null && !mode.trim().isEmpty()) {
            script.setMode(mode.trim());
        }

        // 剧本格式（每集正文输出结构）
        String format = request.getScriptFormat();
        if (format != null && !format.trim().isEmpty()) {
            String v = format.trim().toUpperCase();
            if (!"SCENE".equals(v) && !"STORYBOARD".equals(v) && !"NARRATION".equals(v)) {
                v = "STORYBOARD";
            }
            script.setScriptFormat(v);
        }

        // 分镜数量：不填则按 3 秒一个镜头估算
        int scenes = request.getSceneCount() != null && request.getSceneCount() > 0
                ? request.getSceneCount()
                : Math.max(10, (int) Math.ceil(seconds / 3.0));
        script.setSceneCount(scenes);

        // 计划集数
        script.setEpisodeCount(request.getEpisodeCount() != null ? request.getEpisodeCount() : 10);

        // 配置项
        script.setEnableOutlineUpdate(request.getEnableOutlineUpdate() != null ? request.getEnableOutlineUpdate() : true);
        script.setMinPassScore(request.getMinPassScore() != null ? request.getMinPassScore() : 7);

        // 保存模型配置到 workflowConfig
        if (request.getModelId() != null && !request.getModelId().isEmpty()) {
            script.setWorkflowConfig("{\"modelId\":\"" + request.getModelId() + "\"}");
        }

        return videoScriptService.createScript(script);
    }

    @GetMapping
    public List<VideoScript> list() {
        Long userId = AuthUtils.getCurrentUserId();
        return videoScriptService.getUserScripts(userId);
    }

    @GetMapping("/{id}")
    public VideoScript get(@PathVariable Long id) {
        return videoScriptService.getScript(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        videoScriptService.deleteScript(id);
    }

    @PostMapping("/{id}/start")
    public void start(@PathVariable Long id) {
        videoScriptService.startWorkflow(id);
    }

    @PostMapping("/{id}/pause")
    public void pause(@PathVariable Long id) {
        videoScriptService.pauseWorkflow(id);
    }

    @GetMapping("/{id}/episodes")
    public List<VideoScriptEpisode> getEpisodes(@PathVariable Long id) {
        return videoScriptService.getEpisodes(id);
    }

    @GetMapping("/{id}/episodes/{episodeNumber}")
    public VideoScriptEpisode getEpisode(@PathVariable Long id, @PathVariable Integer episodeNumber) {
        return videoScriptService.getEpisode(id, episodeNumber);
    }

    @PutMapping("/{id}/episodes/{episodeNumber}/content")
    public VideoScriptEpisode updateEpisodeContent(@PathVariable Long id,
                                                   @PathVariable Integer episodeNumber,
                                                   @RequestBody Map<String, String> body) {
        return videoScriptService.updateEpisodeContent(id, episodeNumber, body.get("content"));
    }

    @PostMapping("/{id}/retry/{episodeNumber}")
    public void retryEpisode(@PathVariable Long id, @PathVariable Integer episodeNumber) {
        videoScriptService.retryEpisode(id, episodeNumber);
    }

    @GetMapping("/{id}/logs")
    public List<VideoScriptLog> getLogs(@PathVariable Long id,
                                       @RequestParam(value = "episodeNumber", required = false) Integer episodeNumber,
                                       @PageableDefault(size = 50) Pageable pageable) {
        return videoScriptService.getLogs(id, episodeNumber, pageable);
    }

    @GetMapping("/{id}/workflow/state")
    public VideoScriptWorkflowStateResponse getWorkflowState(@PathVariable Long id) {
        return videoScriptService.getWorkflowState(id);
    }

    @PutMapping("/{id}/config")
    public void updateConfig(@PathVariable Long id, @RequestBody Map<String, String> config) {
        videoScriptService.updateConfig(id, config);
    }
}
