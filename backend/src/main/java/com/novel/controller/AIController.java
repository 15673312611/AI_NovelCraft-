package com.novel.controller;

import com.novel.common.Result;
import com.novel.dto.AIConfigRequest;
import com.novel.service.AITraceRemovalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI工具Controller
 * 提供AI相关的工具功能，如AI消痕等
 */
@RestController
@RequestMapping("/ai")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}, allowCredentials = "true")
public class AIController {

    private static final Logger logger = LoggerFactory.getLogger(AIController.class);

    @Autowired
    private AITraceRemovalService aiTraceRemovalService;

    /**
     * AI消痕接口（流式）
     * 将AI生成的内容进行去AI味处理，使用SSE流式输出
     */
    @PostMapping(value = "/remove-trace-stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter removeAITraceStream(@RequestBody Map<String, Object> request) {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(300000L);
        
        try {
            String content = (String) request.get("content");
            
            if (content == null || content.trim().isEmpty()) {
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name("error").data("内容不能为空"));
                emitter.completeWithError(new Exception("内容不能为空"));
                return emitter;
            }
            
            // 解析AI配置（前端withAIConfig是扁平化的，直接从根级别读取）
            AIConfigRequest aiConfig = new AIConfigRequest();
            if (request.containsKey("provider")) {
                aiConfig.setProvider((String) request.get("provider"));
                aiConfig.setApiKey((String) request.get("apiKey"));
                aiConfig.setModel((String) request.get("model"));
                aiConfig.setBaseUrl((String) request.get("baseUrl"));
                
                logger.info("✅ AI消痕流式 - 收到AI配置: provider={}, model={}", 
                    aiConfig.getProvider(), aiConfig.getModel());
            } else if (request.get("aiConfig") instanceof Map) {
                // 兼容旧的嵌套格式
                @SuppressWarnings("unchecked")
                Map<String, String> aiConfigMap = (Map<String, String>) request.get("aiConfig");
                aiConfig.setProvider(aiConfigMap.get("provider"));
                aiConfig.setApiKey(aiConfigMap.get("apiKey"));
                aiConfig.setModel(aiConfigMap.get("model"));
                aiConfig.setBaseUrl(aiConfigMap.get("baseUrl"));
            }
            
            if (!aiConfig.isValid()) {
                logger.error("❌ AI消痕流式 - AI配置无效: request={}", request);
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name("error").data("AI配置无效，请先在设置页面配置AI服务"));
                emitter.completeWithError(new Exception("AI配置无效"));
                return emitter;
            }
            
            logger.info("🧹 开始AI消痕流式处理，内容长度: {}, 使用模型: {}", content.length(), aiConfig.getModel());
            
            // 异步执行AI消痕
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    aiTraceRemovalService.removeAITraceStream(content, aiConfig, emitter);
                } catch (Exception e) {
                    logger.error("AI消痕流式处理失败", e);
                    try {
                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                            .name("error").data("处理失败: " + e.getMessage()));
                        emitter.completeWithError(e);
                    } catch (Exception ex) {
                        logger.error("发送错误事件失败", ex);
                    }
                }
            });
            
        } catch (Exception e) {
            logger.error("AI消痕初始化失败", e);
            try {
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name("error").data("初始化失败: " + e.getMessage()));
                emitter.completeWithError(e);
            } catch (Exception ex) {
                logger.error("发送错误事件失败", ex);
            }
        }
        
        return emitter;
    }
    
    /**
     * AI消痕接口（非流式，保留作为备用）
     */
    @PostMapping("/remove-trace")
    public Result<Map<String, Object>> removeAITrace(@RequestBody Map<String, Object> request) {
        try {
            String content = (String) request.get("content");
            
            if (content == null || content.trim().isEmpty()) {
                return Result.error("内容不能为空");
            }
            
            // 解析AI配置（前端withAIConfig是扁平化的，直接从根级别读取）
            AIConfigRequest aiConfig = new AIConfigRequest();
            if (request.containsKey("provider")) {
                aiConfig.setProvider((String) request.get("provider"));
                aiConfig.setApiKey((String) request.get("apiKey"));
                aiConfig.setModel((String) request.get("model"));
                aiConfig.setBaseUrl((String) request.get("baseUrl"));
                
                logger.info("✅ AI消痕 - 收到AI配置: provider={}, model={}", 
                    aiConfig.getProvider(), aiConfig.getModel());
            } else if (request.get("aiConfig") instanceof Map) {
                // 兼容旧的嵌套格式
                @SuppressWarnings("unchecked")
                Map<String, String> aiConfigMap = (Map<String, String>) request.get("aiConfig");
                aiConfig.setProvider(aiConfigMap.get("provider"));
                aiConfig.setApiKey(aiConfigMap.get("apiKey"));
                aiConfig.setModel(aiConfigMap.get("model"));
                aiConfig.setBaseUrl(aiConfigMap.get("baseUrl"));
            }
            
            if (!aiConfig.isValid()) {
                logger.error("❌ AI消痕 - AI配置无效: request={}", request);
                return Result.error("AI配置无效，请先在设置页面配置AI服务");
            }
            
            logger.info("🧹 开始AI消痕处理，内容长度: {}, 使用模型: {}", content.length(), aiConfig.getModel());
            
            // 调用AI消痕服务
            String processedContent = aiTraceRemovalService.removeAITrace(content, aiConfig);
            
            logger.info("✅ AI消痕完成，处理后内容长度: {}", processedContent.length());
            
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("processedContent", processedContent);
            result.put("originalLength", content.length());
            result.put("processedLength", processedContent.length());
            
            return Result.success(result);
            
        } catch (Exception e) {
            logger.error("AI消痕处理失败", e);
            return Result.error("AI消痕处理失败: " + e.getMessage());
        }
    }
}

