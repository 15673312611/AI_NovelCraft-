package com.novel.controller;

import com.novel.common.security.SecurityContextTaskWrapper;
import com.novel.domain.entity.AIModel;
import com.novel.dto.AIConfigRequest;
import com.novel.entity.AiGenerator;
import com.novel.service.AIWritingService;
import com.novel.service.AiGeneratorService;
import com.novel.service.SystemAIConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/ai")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}, allowCredentials = "true")
public class AIChatController {

    private static final Logger logger = LoggerFactory.getLogger(AIChatController.class);

    @Autowired
    private AIWritingService aiWritingService;

    @Autowired
    private SystemAIConfigService systemAIConfigService;

    @Autowired
    private AiGeneratorService aiGeneratorService;

    @PostMapping(value = "/chat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, Object> request) {
        SseEmitter emitter = new SseEmitter(300000L);

        try {
            String modelId = request.get("preferredModel") == null
                    ? (String) request.get("model")
                    : String.valueOf(request.get("preferredModel"));
            Long generatorId = parseLong(request.get("generatorId"));

            @SuppressWarnings("unchecked")
            List<Map<String, String>> messages = (List<Map<String, String>>) request.get("messages");

            List<Map<String, String>> sanitizedMessages = sanitizeMessages(messages, generatorId);
            if (sanitizedMessages.isEmpty()) {
                emitter.send(SseEmitter.event().name("error").data("Messages cannot be empty"));
                emitter.complete();
                return emitter;
            }

            AIModel aiModel = systemAIConfigService.getModel(modelId);
            if (aiModel == null) {
                emitter.send(SseEmitter.event().name("error").data("No available AI model is configured"));
                emitter.complete();
                return emitter;
            }

            Map<String, String> apiConfig = systemAIConfigService.getModelAPIConfig(aiModel);
            if (apiConfig.get("apiKey") == null || apiConfig.get("apiKey").isEmpty()) {
                emitter.send(SseEmitter.event().name("error").data("AI service is not configured"));
                emitter.complete();
                return emitter;
            }

            AIConfigRequest aiConfig = new AIConfigRequest();
            aiConfig.setProvider(apiConfig.get("provider"));
            aiConfig.setModel(apiConfig.get("model"));
            aiConfig.setApiKey(apiConfig.get("apiKey"));
            aiConfig.setBaseUrl(apiConfig.get("baseUrl"));

            logger.info("Start ai chat, model={}, messages={}", aiModel.getModelId(), sanitizedMessages.size());

            CompletableFuture.runAsync(SecurityContextTaskWrapper.wrap(() -> {
                try {
                    aiWritingService.streamGenerateContentWithMessages(
                            sanitizedMessages,
                            "ai_chat",
                            aiConfig,
                            chunk -> {
                                try {
                                    String jsonData = "{\"choices\":[{\"delta\":{\"content\":\""
                                            + escapeJson(chunk) + "\"}}]}";
                                    emitter.send(SseEmitter.event().data(jsonData));
                                } catch (Exception e) {
                                    logger.error("Failed to send sse chunk", e);
                                }
                            }
                    );

                    emitter.send(SseEmitter.event().data("[DONE]"));
                    emitter.complete();
                    logger.info("Ai chat completed");
                } catch (Exception e) {
                    logger.error("Ai chat failed", e);
                    try {
                        emitter.send(SseEmitter.event().name("error").data("Generation failed: " + e.getMessage()));
                        emitter.completeWithError(e);
                    } catch (Exception ex) {
                        logger.error("Failed to send error event", ex);
                    }
                }
            }));
        } catch (Exception e) {
            logger.error("Failed to initialize ai chat", e);
            try {
                emitter.send(SseEmitter.event().name("error").data("Initialization failed: " + e.getMessage()));
                emitter.completeWithError(e);
            } catch (Exception ex) {
                logger.error("Failed to send initialization error", ex);
            }
        }

        return emitter;
    }

    private List<Map<String, String>> sanitizeMessages(List<Map<String, String>> rawMessages, Long generatorId) {
        List<Map<String, String>> sanitizedMessages = new ArrayList<>();

        if (generatorId != null) {
            AiGenerator generator = aiGeneratorService.getGeneratorWithPromptById(generatorId);
            if (generator.getPrompt() != null && !generator.getPrompt().trim().isEmpty()) {
                sanitizedMessages.add(buildMessage("system", generator.getPrompt()));
            }
        }

        if (rawMessages == null) {
            return sanitizedMessages;
        }

        for (Map<String, String> message : rawMessages) {
            if (message == null) {
                continue;
            }

            String role = message.get("role");
            String content = message.get("content");
            if (!isSupportedChatRole(role) || content == null || content.trim().isEmpty()) {
                continue;
            }

            sanitizedMessages.add(buildMessage(role, content));
        }

        return sanitizedMessages;
    }

    private boolean isSupportedChatRole(String role) {
        return "user".equals(role) || "assistant".equals(role);
    }

    private Map<String, String> buildMessage(String role, String content) {
        Map<String, String> message = new HashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
