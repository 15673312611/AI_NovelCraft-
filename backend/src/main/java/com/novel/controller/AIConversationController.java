package com.novel.controller;

import com.novel.dto.AIConversationSummaryDto;
import com.novel.service.AIConversationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/novels/{novelId}/ai-history")
@CrossOrigin(origins = "*")
@Slf4j
public class AIConversationController {

    @Autowired
    private AIConversationService conversationService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getConversations(@PathVariable Long novelId) {
        log.info("API: get ai conversations for novel={}", novelId);
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put(
                    "data",
                    conversationService.getConversationsByNovelId(novelId).stream()
                            .map(AIConversationSummaryDto::from)
                            .collect(Collectors.toList())
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get ai conversations", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteConversation(
            @PathVariable Long novelId,
            @PathVariable Long id) {
        log.info("API: delete ai conversation={}, novel={}", id, novelId);
        try {
            conversationService.deleteConversation(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Conversation deleted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to delete ai conversation", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> clearConversations(@PathVariable Long novelId) {
        log.info("API: clear ai conversations for novel={}", novelId);
        try {
            conversationService.clearConversationsByNovelIdForCurrentUser(novelId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Conversation history cleared");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to clear ai conversations", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}
