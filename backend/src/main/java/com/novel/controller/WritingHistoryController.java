package com.novel.controller;

import com.novel.entity.WritingVersionHistory;
import com.novel.service.WritingVersionHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 写作版本历史查询控制器
 *
 * 主要提供：
 * - 按章节ID查询历史版本
 * - 按文档ID查询历史版本
 */
@RestController
@CrossOrigin(origins = "*")
@Slf4j
public class WritingHistoryController {

    @Autowired
    private WritingVersionHistoryService historyService;

    /**
     * 获取章节的历史版本列表
     */
    @GetMapping("/chapters/{chapterId}/history")
    public ResponseEntity<Map<String, Object>> getChapterHistory(
            @PathVariable Long chapterId,
            @RequestParam(value = "limit", required = false, defaultValue = "50") Integer limit) {
        log.info("API: 获取章节ID={}的版本历史, limit={}", chapterId, limit);
        try {
            List<WritingVersionHistory> histories = historyService.getChapterHistory(chapterId, limit);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", histories);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取章节版本历史失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 获取辅助文档的历史版本列表
     * （目前正文主要使用章节表，文档历史为可选功能）
     */
    @GetMapping("/documents/{documentId}/history")
    public ResponseEntity<Map<String, Object>> getDocumentHistory(
            @PathVariable Long documentId,
            @RequestParam(value = "limit", required = false, defaultValue = "50") Integer limit) {
        log.info("API: 获取文档ID={}的版本历史, limit={}", documentId, limit);
        try {
            List<WritingVersionHistory> histories = historyService.getDocumentHistory(documentId, limit);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", histories);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取文档版本历史失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}

