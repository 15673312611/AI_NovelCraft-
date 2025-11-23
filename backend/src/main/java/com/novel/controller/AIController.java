package com.novel.controller;

import com.novel.common.Result;
import com.novel.dto.AIConfigRequest;
import com.novel.service.AIPolishService;
import com.novel.service.AITraceRemovalService;
import com.novel.service.AIProofreadService;
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

    private final AITraceRemovalService aiTraceRemovalService;
    private final com.novel.service.AIManuscriptReviewService manuscriptReviewService;
    private final AIPolishService aiPolishService;
    private final com.novel.service.AIStreamlineService streamlineService;
    private final AIProofreadService aiProofreadService;

    @Autowired
    public AIController(
            AITraceRemovalService aiTraceRemovalService,
            com.novel.service.AIManuscriptReviewService manuscriptReviewService,
            AIPolishService aiPolishService,
            com.novel.service.AIStreamlineService streamlineService,
            AIProofreadService aiProofreadService
    ) {
        this.aiTraceRemovalService = aiTraceRemovalService;
        this.manuscriptReviewService = manuscriptReviewService;
        this.aiPolishService = aiPolishService;
        this.streamlineService = streamlineService;
        this.aiProofreadService = aiProofreadService;
    }

    @PostMapping("/polish-selection")
    public Result<Map<String, Object>> polishSelection(@RequestBody Map<String, Object> request) {
        try {
            String fullContent = (String) request.getOrDefault("chapterContent", request.get("content"));
            String selection = (String) request.getOrDefault("selection", request.get("selectedText"));
            String instructions = (String) request.getOrDefault("instructions", request.get("requirement"));
            String chapterTitle = (String) request.getOrDefault("chapterTitle", request.get("title"));

            if (selection == null || selection.trim().isEmpty()) {
                return Result.error("待润色片段不能为空");
            }

            AIConfigRequest aiConfig = new AIConfigRequest();
            if (request.containsKey("provider")) {
                aiConfig.setProvider((String) request.get("provider"));
                aiConfig.setApiKey((String) request.get("apiKey"));
                aiConfig.setModel((String) request.get("model"));
                aiConfig.setBaseUrl((String) request.get("baseUrl"));
            } else if (request.get("aiConfig") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> aiConfigMap = (Map<String, String>) request.get("aiConfig");
                aiConfig.setProvider(aiConfigMap.get("provider"));
                aiConfig.setApiKey(aiConfigMap.get("apiKey"));
                aiConfig.setModel(aiConfigMap.get("model"));
                aiConfig.setBaseUrl(aiConfigMap.get("baseUrl"));
            }

            if (!aiConfig.isValid()) {
                return Result.error("AI配置无效，请先在设置页面配置AI服务");
            }

            String polished = aiPolishService.polishSelection(
                    chapterTitle,
                    fullContent,
                    selection,
                    instructions,
                    aiConfig
            );

            Map<String, Object> data = new java.util.HashMap<>();
            data.put("polishedContent", polished);
            return Result.success(data);

        } catch (Exception e) {
            logger.error("AI润色片段失败", e);
            return Result.error("AI润色失败: " + e.getMessage());
        }
    }

    /**
     * AI智能纠错接口
     * 前端通过 /ai/proofread 调用，返回检测到的错误列表
     */
    @PostMapping("/proofread")
    public Result<Map<String, Object>> proofread(@RequestBody Map<String, Object> request) {
        try {
            String content = (String) request.get("content");

            if (content == null || content.trim().isEmpty()) {
                return Result.error("内容不能为空");
            }

            // 角色名称列表（可选）
            java.util.List<String> characterNames = new java.util.ArrayList<>();
            Object namesObj = request.get("characterNames");
            if (namesObj instanceof java.util.List) {
                for (Object o : (java.util.List<?>) namesObj) {
                    if (o != null) {
                        characterNames.add(o.toString());
                    }
                }
            }

            // 解析AI配置（前端withAIConfig是扁平化的，直接从根级别读取；同时兼容嵌套aiConfig）
            AIConfigRequest aiConfig = new AIConfigRequest();
            if (request.containsKey("provider")) {
                aiConfig.setProvider((String) request.get("provider"));
                aiConfig.setApiKey((String) request.get("apiKey"));
                aiConfig.setModel((String) request.get("model"));
                aiConfig.setBaseUrl((String) request.get("baseUrl"));
            } else if (request.get("aiConfig") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> aiConfigMap = (Map<String, String>) request.get("aiConfig");
                aiConfig.setProvider(aiConfigMap.get("provider"));
                aiConfig.setApiKey(aiConfigMap.get("apiKey"));
                aiConfig.setModel(aiConfigMap.get("model"));
                aiConfig.setBaseUrl(aiConfigMap.get("baseUrl"));
            }

            if (!aiConfig.isValid()) {
                logger.error("❌ AI纠错 - AI配置无效: request={}", request);
                return Result.error("AI配置无效，请先在设置页面配置AI服务");
            }

            java.util.List<AIProofreadService.ProofreadError> errors =
                    aiProofreadService.proofread(content, characterNames, aiConfig);

            Map<String, Object> data = new java.util.HashMap<>();
            data.put("errors", errors);
            data.put("errorCount", errors != null ? errors.size() : 0);
            return Result.success(data);

        } catch (Exception e) {
            logger.error("AI纠错失败", e);
            return Result.error("AI纠错失败: " + e.getMessage());
        }
    }

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
    
    /**
     * AI审稿接口（流式）
     * 对稿件内容进行专业审稿，提供修改建议，使用SSE流式输出
     */
    @PostMapping(value = "/review-manuscript-stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter reviewManuscriptStream(@RequestBody Map<String, Object> request) {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(300000L);
        
        try {
            String content = (String) request.get("content");
            
            if (content == null || content.trim().isEmpty()) {
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name("error").data("稿件内容不能为空"));
                emitter.completeWithError(new Exception("稿件内容不能为空"));
                return emitter;
            }
            
            // 解析AI配置
            AIConfigRequest aiConfig = new AIConfigRequest();
            if (request.containsKey("provider")) {
                aiConfig.setProvider((String) request.get("provider"));
                aiConfig.setApiKey((String) request.get("apiKey"));
                aiConfig.setModel((String) request.get("model"));
                aiConfig.setBaseUrl((String) request.get("baseUrl"));
                
                logger.info("✅ AI审稿流式 - 收到AI配置: provider={}, model={}", 
                    aiConfig.getProvider(), aiConfig.getModel());
            } else if (request.get("aiConfig") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> aiConfigMap = (Map<String, String>) request.get("aiConfig");
                aiConfig.setProvider(aiConfigMap.get("provider"));
                aiConfig.setApiKey(aiConfigMap.get("apiKey"));
                aiConfig.setModel(aiConfigMap.get("model"));
                aiConfig.setBaseUrl(aiConfigMap.get("baseUrl"));
            }
            
            if (!aiConfig.isValid()) {
                logger.error("❌ AI审稿流式 - AI配置无效: request={}", request);
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name("error").data("AI配置无效，请先在设置页面配置AI服务"));
                emitter.completeWithError(new Exception("AI配置无效"));
                return emitter;
            }
            
            logger.info("🔍 开始AI审稿流式处理，稿件长度: {}, 使用模型: {}", content.length(), aiConfig.getModel());
            
            // 异步执行AI审稿
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    manuscriptReviewService.reviewManuscriptStream(content, aiConfig, emitter);
                } catch (Exception e) {
                    logger.error("AI审稿流式处理失败", e);
                    try {
                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                            .name("error").data("审稿失败: " + e.getMessage()));
                        emitter.completeWithError(e);
                    } catch (Exception ex) {
                        logger.error("发送错误事件失败", ex);
                    }
                }
            });
            
        } catch (Exception e) {
            logger.error("AI审稿初始化失败", e);
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
     * AI精简接口（流式）
     * 对章节内容进行精简优化，去除冗余片段，加快剧情节奏，使用SSE流式输出
     */
    @PostMapping(value = "/streamline-content-stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamlineContentStream(@RequestBody Map<String, Object> request) {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(300000L);
        
        try {
            String content = (String) request.get("content");
            
            if (content == null || content.trim().isEmpty()) {
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name("error").data("内容不能为空"));
                emitter.completeWithError(new Exception("内容不能为空"));
                return emitter;
            }
            
            // 解析前端传入的目标字数（可选）
            Integer parsedTargetLength = null;
            Object targetLengthObj = request.get("targetLength");
            if (targetLengthObj != null) {
                try {
                    if (targetLengthObj instanceof Number) {
                        parsedTargetLength = ((Number) targetLengthObj).intValue();
                    } else {
                        parsedTargetLength = Integer.parseInt(targetLengthObj.toString());
                    }
                } catch (NumberFormatException ignore) {
                    parsedTargetLength = null;
                }
            }
            final Integer targetLength = parsedTargetLength;
            
            // 解析AI配置
            AIConfigRequest aiConfig = new AIConfigRequest();
            if (request.containsKey("provider")) {
                aiConfig.setProvider((String) request.get("provider"));
                aiConfig.setApiKey((String) request.get("apiKey"));
                aiConfig.setModel((String) request.get("model"));
                aiConfig.setBaseUrl((String) request.get("baseUrl"));
                
                logger.info("✅ AI精简流式 - 收到AI配置: provider={}, model={}", 
                    aiConfig.getProvider(), aiConfig.getModel());
            } else if (request.get("aiConfig") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> aiConfigMap = (Map<String, String>) request.get("aiConfig");
                aiConfig.setProvider(aiConfigMap.get("provider"));
                aiConfig.setApiKey(aiConfigMap.get("apiKey"));
                aiConfig.setModel(aiConfigMap.get("model"));
                aiConfig.setBaseUrl(aiConfigMap.get("baseUrl"));
            }
            
            if (!aiConfig.isValid()) {
                logger.error("❌ AI精简流式 - AI配置无效: request={}", request);
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name("error").data("AI配置无效，请先在设置页面配置AI服务"));
                emitter.completeWithError(new Exception("AI配置无效"));
                return emitter;
            }
            
            logger.info("✂️ 开始AI精简流式处理，内容长度: {}, 使用模型: {}, targetLength: {}", content.length(), aiConfig.getModel(), targetLength);
            
            // 异步执行AI精简
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    streamlineService.streamlineContentStream(content, targetLength, aiConfig, emitter);
                } catch (Exception e) {
                    logger.error("AI精简流式处理失败", e);
                    try {
                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                            .name("error").data("精简失败: " + e.getMessage()));
                        emitter.completeWithError(e);
                    } catch (Exception ex) {
                        logger.error("发送错误事件失败", ex);
                    }
                }
            });
            
        } catch (Exception e) {
            logger.error("AI精简初始化失败", e);
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
}
