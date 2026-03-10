package com.novel.shortstory.controller;

import com.novel.common.security.AuthUtils;
import com.novel.shortstory.dto.CreateShortStoryRequest;
import com.novel.shortstory.dto.WorkflowStateResponse;
import com.novel.shortstory.entity.ShortNovel;
import com.novel.shortstory.entity.ShortChapter;
import com.novel.shortstory.entity.WorkflowLog;
import com.novel.shortstory.service.ShortStoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/short-stories")
public class ShortStoryController {
    
    @Autowired
    private ShortStoryService shortStoryService;
    
    @PostMapping
    public ShortNovel create(@RequestBody CreateShortStoryRequest request) {
        Long userId = AuthUtils.getCurrentUserId();
        
        ShortNovel novel = new ShortNovel();
        novel.setUserId(userId);
        novel.setTitle(request.getTitle());
        novel.setIdea(request.getIdea());
        novel.setTargetWords(request.getTargetWords() != null ? request.getTargetWords() : 30000);
        novel.setChapterCount(request.getChapterCount() != null ? request.getChapterCount() : 10);
        novel.setEnableOutlineUpdate(request.getEnableOutlineUpdate() != null ? request.getEnableOutlineUpdate() : true);
        novel.setMinPassScore(request.getMinPassScore() != null ? request.getMinPassScore() : 7);
        novel.setWordsPerChapter(novel.getTargetWords() / novel.getChapterCount());
        
        // 保存模型配置到 workflowConfig
        if (request.getModelId() != null && !request.getModelId().isEmpty()) {
            novel.setWorkflowConfig("{\"modelId\":\"" + request.getModelId() + "\"}");
        }
        
        return shortStoryService.createNovel(novel);
    }
    
    @GetMapping
    public List<ShortNovel> list() {
        Long userId = AuthUtils.getCurrentUserId();
        return shortStoryService.getUserNovels(userId);
    }
    
    @GetMapping("/{id}")
    public ShortNovel get(@PathVariable Long id) {
        return shortStoryService.getNovel(id);
    }
    
    
    @GetMapping("/{id}/chapters")
    public List<ShortChapter> getChapters(@PathVariable Long id) {
        return shortStoryService.getChapters(id);
    }
    
    

    /**
     * 前端编辑器保存接口：按 novelId + chapterNumber 更新正文
     */
    @PutMapping("/{id}/chapters/{chapterNumber}/content")
    public ShortChapter updateChapterContent(@PathVariable Long id,
                                             @PathVariable Integer chapterNumber,
                                             @RequestBody Map<String, String> body) {
        return shortStoryService.updateChapterContent(id, chapterNumber, body.get("content"));
    }
    
    @PostMapping("/{id}/start")
    public void start(@PathVariable Long id) {
        shortStoryService.startWorkflow(id);
    }
    
    @PostMapping("/{id}/pause")
    public void pause(@PathVariable Long id) {
        shortStoryService.pauseWorkflow(id);
    }
    
    @PostMapping("/{id}/retry/{chapterNumber}")
    public void retry(@PathVariable Long id, @PathVariable Integer chapterNumber) {
        shortStoryService.retryChapter(id, chapterNumber);
    }
    
    @GetMapping("/{id}/logs")
    public List<WorkflowLog> getLogs(@PathVariable Long id, @PageableDefault(size = 50) Pageable pageable) {
        return shortStoryService.getLogs(id, pageable);
    }

    @GetMapping("/{id}/workflow/state")
    public WorkflowStateResponse getWorkflowState(@PathVariable Long id) {
        return shortStoryService.getWorkflowState(id);
    }
    
    @PutMapping("/{id}/config")
    public void updateConfig(@PathVariable Long id, @RequestBody Map<String, String> config) {
        shortStoryService.updateConfig(id, config);
    }
}
