package com.novel.shortstory.service;

import com.novel.shortstory.dto.WorkflowStateResponse;
import com.novel.shortstory.entity.ShortNovel;
import com.novel.shortstory.entity.ShortChapter;
import com.novel.shortstory.entity.WorkflowLog;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface ShortStoryService {
    ShortNovel createNovel(ShortNovel novel);
    ShortNovel getNovel(Long id);
    List<ShortNovel> getUserNovels(Long userId);
    
    // 章节管理
    List<ShortChapter> getChapters(Long novelId);
    ShortChapter updateChapterContent(Long novelId, Integer chapterNumber, String content);
    
    // 工作流控制
    void startWorkflow(Long novelId);
    void pauseWorkflow(Long novelId);
    void retryChapter(Long novelId, Integer chapterNumber);
    
    // 日志
    List<WorkflowLog> getLogs(Long novelId, Pageable pageable);

    // 工作流状态（用于前端画布）
    WorkflowStateResponse getWorkflowState(Long novelId);
    
    // 更新配置（模型等）
    void updateConfig(Long novelId, Map<String, String> config);
}
