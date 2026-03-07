package com.novel.controller;

import com.novel.dto.AIConfigRequest;
import com.novel.domain.entity.AIModel;
import com.novel.service.AIWritingService;
import com.novel.service.SystemAIConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AI 聊天控制器
 * 提供通用的 AI 聊天流式接口
 */
@RestController
@RequestMapping("/ai")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}, allowCredentials = "true")
public class AIChatController {

    private static final Logger logger = LoggerFactory.getLogger(AIChatController.class);

    @Autowired
    private AIWritingService aiWritingService;

    @Autowired
    private SystemAIConfigService systemAIConfigService;

    /**
     * 流式聊天接口
     */
    @PostMapping(value = "/chat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, Object> request) {
        SseEmitter emitter = new SseEmitter(300000L); // 5分钟超时

        try {
            // 获取请求参数
            String modelId = (String) request.get("model");
            @SuppressWarnings("unchecked")
            List<Map<String, String>> messages = (List<Map<String, String>>) request.get("messages");
            
            if (messages == null || messages.isEmpty()) {
                emitter.send(SseEmitter.event().name("error").data("消息不能为空"));
                emitter.complete();
                return emitter;
            }

            // 获取模型配置
            AIModel aiModel = systemAIConfigService.getModel(modelId);
            
            if (aiModel == null) {
                emitter.send(SseEmitter.event().name("error").data("没有可用的 AI 模型，请联系管理员配置"));
                emitter.complete();
                return emitter;
            }

            // 获取 API 配置
            Map<String, String> apiConfig = systemAIConfigService.getModelAPIConfig(aiModel);
            
            if (apiConfig.get("apiKey") == null || apiConfig.get("apiKey").isEmpty()) {
                emitter.send(SseEmitter.event().name("error").data("AI 服务未配置，请联系管理员"));
                emitter.complete();
                return emitter;
            }

            // 构建 AI 配置
            AIConfigRequest aiConfig = new AIConfigRequest();
            aiConfig.setProvider(apiConfig.get("provider"));
            aiConfig.setModel(apiConfig.get("model"));
            aiConfig.setApiKey(apiConfig.get("apiKey"));
            aiConfig.setBaseUrl(apiConfig.get("baseUrl"));

            logger.info("🤖 开始 AI 聊天，模型: {}, 消息数: {}", aiModel.getModelId(), messages.size());

            // 异步执行流式生成
            CompletableFuture.runAsync(() -> {
                try {
                    aiWritingService.streamGenerateContentWithMessages(
                        messages,
                        "ai_chat",
                        aiConfig,
                        chunk -> {
                            try {
                                // 发送 SSE 格式的数据
                                String jsonData = "{\"choices\":[{\"delta\":{\"content\":\"" + 
                                    escapeJson(chunk) + "\"}}]}";
                                emitter.send(SseEmitter.event().data(jsonData));
                            } catch (Exception e) {
                                logger.error("发送数据失败", e);
                            }
                        }
                    );
                    
                    // 发送完成标记
                    emitter.send(SseEmitter.event().data("[DONE]"));
                    emitter.complete();
                    logger.info("✅ AI 聊天完成");
                    
                } catch (Exception e) {
                    logger.error("AI 聊天失败", e);
                    try {
                        emitter.send(SseEmitter.event().name("error").data("生成失败: " + e.getMessage()));
                        emitter.completeWithError(e);
                    } catch (Exception ex) {
                        logger.error("发送错误事件失败", ex);
                    }
                }
            });

        } catch (Exception e) {
            logger.error("AI 聊天初始化失败", e);
            try {
                emitter.send(SseEmitter.event().name("error").data("初始化失败: " + e.getMessage()));
                emitter.completeWithError(e);
            } catch (Exception ex) {
                logger.error("发送错误事件失败", ex);
            }
        }

        return emitter;
    }

    /**
     * 转义 JSON 字符串中的特殊字符
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
