package com.novel.script.service;

import com.novel.script.dto.VideoScriptWorkflowStateResponse;
import com.novel.script.entity.VideoScript;
import com.novel.script.entity.VideoScriptEpisode;
import com.novel.script.entity.VideoScriptLog;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface VideoScriptService {
    VideoScript createScript(VideoScript script);
    VideoScript getScript(Long id);
    List<VideoScript> getUserScripts(Long userId);
    // Episodes
    List<VideoScriptEpisode> getEpisodes(Long scriptId);
    VideoScriptEpisode updateEpisodeContent(Long scriptId, Integer episodeNumber, String content);

    // 工作流控制
    void startWorkflow(Long scriptId);
    void pauseWorkflow(Long scriptId);
    void retryEpisode(Long scriptId, Integer episodeNumber);

    // 日志
    List<VideoScriptLog> getLogs(Long scriptId, Integer episodeNumber, Pageable pageable);

    // 工作流状态（用于前端画布）
    VideoScriptWorkflowStateResponse getWorkflowState(Long scriptId);

    // 更新配置（模型等）
    void updateConfig(Long scriptId, Map<String, String> config);
}
