package com.novel.controller;

import com.novel.common.Result;
import com.novel.domain.entity.Novel;
import com.novel.service.NovelCraftAIService;
import com.novel.service.NovelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NovelCraft AI Controller
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
}
