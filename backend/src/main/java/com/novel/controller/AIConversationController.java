package com.novel.controller;

import com.novel.entity.AIConversation;
import com.novel.service.AIConversationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI对话历史控制器
 */
@RestController
@RequestMapping("/novels/{novelId}/ai-history")
@CrossOrigin(origins = "*")
@Slf4j
public class AIConversationController {

    @Autowired
    private AIConversationService conversationService;

    /**
     * 获取小说的AI对话历史
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getConversations(@PathVariable Long novelId) {
        log.info("API: 获取小说ID={}的对话历史", novelId);
        try {
            List<AIConversation> conversations = conversationService.getConversationsByNovelId(novelId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", conversations);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取对话历史失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 删除对话记录
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteConversation(
            @PathVariable Long novelId,
            @PathVariable Long id) {
        log.info("API: 删除对话记录ID={}", id);
        try {
            conversationService.deleteConversation(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "对话记录删除成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("删除对话记录失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 清空小说的对话历史
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> clearConversations(@PathVariable Long novelId) {
        log.info("API: 清空小说ID={}的对话历史", novelId);
        try {
            conversationService.clearConversationsByNovelId(novelId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "对话历史已清空");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("清空对话历史失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}

