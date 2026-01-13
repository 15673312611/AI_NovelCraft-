package com.novel.agentic.controller;

import com.novel.agentic.dto.ChapterGenerationRequest;
import com.novel.agentic.service.AgenticChapterWriter;
import com.novel.dto.AIConfigRequest;
import com.novel.service.ChapterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 代理式AI写作测试控制器
 * 
 * 注意：这是独立的测试接口，不影响现有写作流程
 */
@RestController
@RequestMapping("/agentic")
@CrossOrigin(origins = "*")
public class AgenticWritingController {
    
    private static final Logger logger = LoggerFactory.getLogger(AgenticWritingController.class);
    
    @Autowired
    private AgenticChapterWriter chapterWriter;
    
    @Autowired
    private ChapterService chapterService;
    
    /**
     * 测试接口：使用代理式AI生成章节
     * 
     * 请求参数：
     * - novelId: 小说ID（必须已有大纲和卷蓝图）
     * - startChapter: 起始章节号
     * - count: 生成章节数量（默认1）
     * - userAdjustment: 用户创作要求（可选）
     * - aiConfig: AI配置（可选）
     * 
     * 返回：SSE流式响应
     */
    @PostMapping("/generate-chapters-stream")
    public SseEmitter generateChaptersStream(@RequestBody Map<String, Object> requestMap) {
        // 解析并验证请求参数
        ChapterGenerationRequest request = parseAndValidateRequest(requestMap);
        
        logger.info("📝 代理式AI写作请求: novelId={}, 起始章节={}, 数量={}, 风格提示词={}", 
            request.getNovelId(), request.getStartChapter(), request.getCount(), 
            request.getStylePromptFile() != null ? request.getStylePromptFile() : "默认");
        
        // 创建SSE发射器并设置心跳
        SseEmitter emitter = new SseEmitter(0L);
        ScheduledExecutorService heartbeat = setupHeartbeat(emitter, request.getNovelId());
        
        // 捕获安全上下文用于异步执行
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // 异步执行章节生成
        executeChapterGenerationAsync(request, emitter, heartbeat, authentication);
        
        return emitter;
    }
    
    /**
     * 获取系统状态
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("version", "1.0.0-agentic");
        status.put("status", "running");
        status.put("description", "代理式AI写作系统（测试版）");
        List<String> features = new java.util.ArrayList<>();
        features.add("ReAct决策循环");
        features.add("智能工具选择");
        features.add("图谱上下文检索");
        features.add("批量章节生成");
        status.put("features", features);
        return status;
    }
    
    /**
     * 解析并验证请求参数
     */
    private ChapterGenerationRequest parseAndValidateRequest(Map<String, Object> requestMap) {
        // 验证必需参数
        if (!requestMap.containsKey("novelId") || requestMap.get("novelId") == null) {
            throw new IllegalArgumentException("novelId 不能为空");
        }
        
        ChapterGenerationRequest request = new ChapterGenerationRequest();
        
        // 解析 novelId
        Long novelId = ((Number) requestMap.get("novelId")).longValue();
        request.setNovelId(novelId);
        
        // 解析起始章节号
        Integer startChapter = extractStartChapter(requestMap, novelId);
        request.setStartChapter(startChapter);
        
        // 解析章节数量，默认为1
        Integer count = requestMap.containsKey("count") && requestMap.get("count") != null
            ? ((Number) requestMap.get("count")).intValue() : 1;
        if (count < 1) {
            throw new IllegalArgumentException("count 必须大于0");
        }
        request.setCount(count);
        
        // 解析其他可选参数
        request.setUserAdjustment((String) requestMap.get("userAdjustment"));
        request.setStylePromptFile((String) requestMap.get("stylePromptFile"));
        request.setPromptTemplateId(extractPromptTemplateId(requestMap));
        request.setReferenceContents(extractReferenceContents(requestMap));
        request.setAiConfig(extractAIConfig(requestMap));
        
        return request;
    }
    
    /**
     * 提取提示词模板ID
     */
    private Long extractPromptTemplateId(Map<String, Object> requestMap) {
        Object templateIdObj = requestMap.get("promptTemplateId");
        if (templateIdObj instanceof Number) {
            return ((Number) templateIdObj).longValue();
        }
        return null;
    }
    
    /**
     * 提取起始章节号
     */
    private Integer extractStartChapter(Map<String, Object> requestMap, Long novelId) {
        Integer startChapter = null;
        
        if (requestMap.containsKey("startChapter") && requestMap.get("startChapter") != null) {
            startChapter = ((Number) requestMap.get("startChapter")).intValue();
        } else {
            startChapter = chapterService.getNextChapterNumber(novelId);
            logger.info("📌 未显式指定起始章节，自动从数据库最近一章推算下一章: {}", startChapter);
        }
        
        if (startChapter == null || startChapter < 1) {
            startChapter = 1;
        }
        
        return startChapter;
    }
    
    /**
     * 设置SSE心跳机制
     */
    private ScheduledExecutorService setupHeartbeat(SseEmitter emitter, Long novelId) {
        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "agentic-sse-heartbeat");
            t.setDaemon(true);
            return t;
        });
        
        Runnable stopHeartbeat = () -> {
            if (!heartbeat.isShutdown()) {
                heartbeat.shutdownNow();
            }
        };
        
        // 每20秒发送一次心跳
        heartbeat.scheduleAtFixedRate(
            () -> safeSend(emitter, SseEmitter.event().name("keepalive").data("💓")),
            0, 20, TimeUnit.SECONDS
        );
        
        // 设置SSE事件处理器
        emitter.onTimeout(() -> {
            logger.warn("SSE连接超时: novelId={}", novelId);
            stopHeartbeat.run();
            emitter.complete();
        });
        emitter.onCompletion(stopHeartbeat);
        emitter.onError(throwable -> {
            logger.error("SSE连接错误", throwable);
            stopHeartbeat.run();
        });
        
        return heartbeat;
    }
    
    /**
     * 异步执行章节生成
     */
    private void executeChapterGenerationAsync(
            ChapterGenerationRequest request,
            SseEmitter emitter,
            ScheduledExecutorService heartbeat,
            Authentication authentication) {
        
        CompletableFuture.runAsync(() -> {
            // 设置异步线程的安全上下文
            SecurityContext asyncContext = SecurityContextHolder.createEmptyContext();
            asyncContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(asyncContext);
            
            try {
                // 根据数量选择生成方法
                if (request.getCount() == 1) {
                    chapterWriter.generateChapter(
                        request.getNovelId(),
                        request.getStartChapter(),
                        request.getUserAdjustment(),
                        request.getAiConfig(),
                        request.getStylePromptFile(),
                        request.getPromptTemplateId(),
                        request.getReferenceContents(),
                        emitter
                    );
                } else {
                    chapterWriter.generateMultipleChapters(
                        request.getNovelId(),
                        request.getStartChapter(),
                        request.getCount(),
                        request.getAiConfig(),
                        request.getStylePromptFile(),
                        request.getPromptTemplateId(),
                        request.getReferenceContents(),
                        emitter
                    );
                }
                
                // 成功完成
                emitter.complete();
                
            } catch (Exception e) {
                logger.error("代理式AI写作失败: novelId={}, chapter={}", 
                    request.getNovelId(), request.getStartChapter(), e);
                handleGenerationError(emitter, e);
            } finally {
                // 清理资源
                shutdownHeartbeat(heartbeat);
                SecurityContextHolder.clearContext();
            }
        });
    }
    
    /**
     * 处理生成错误
     */
    private void handleGenerationError(SseEmitter emitter, Exception e) {
        try {
            safeSend(emitter, SseEmitter.event()
                .name("error")
                .data("生成失败: " + e.getMessage()));
            emitter.completeWithError(e);
        } catch (Exception ex) {
            logger.error("发送错误事件失败", ex);
        }
    }
    
    /**
     * 关闭心跳服务
     */
    private void shutdownHeartbeat(ScheduledExecutorService heartbeat) {
        if (heartbeat != null && !heartbeat.isShutdown()) {
            heartbeat.shutdownNow();
        }
    }
    
    /**
     * 提取AI配置
     */
    @SuppressWarnings("unchecked")
    private AIConfigRequest extractAIConfig(Map<String, Object> request) {
        AIConfigRequest config = new AIConfigRequest();
        
        // 首先尝试从 aiConfig 对象中提取
        Object aiConfigObj = request.get("aiConfig");
        if (aiConfigObj instanceof Map) {
            Map<String, Object> configMap = (Map<String, Object>) aiConfigObj;
            
            if (configMap.containsKey("provider")) {
                config.setProvider((String) configMap.get("provider"));
            }
            if (configMap.containsKey("model")) {
                config.setModel((String) configMap.get("model"));
            }
            if (configMap.containsKey("apiKey")) {
                config.setApiKey((String) configMap.get("apiKey"));
            }
            if (configMap.containsKey("baseUrl")) {
                config.setBaseUrl((String) configMap.get("baseUrl"));
            }
            if (configMap.containsKey("temperature")) {
                Object temp = configMap.get("temperature");
                if (temp instanceof Number) {
                    config.setTemperature(((Number) temp).doubleValue());
                }
            }
        }
        
        // 然后从顶层参数提取（优先级更高）
        if (request.containsKey("preferredModel")) {
            config.setModel((String) request.get("preferredModel"));
        }
        if (request.containsKey("temperature")) {
            Object temp = request.get("temperature");
            if (temp instanceof Number) {
                config.setTemperature(((Number) temp).doubleValue());
            }
        }
        
        return config;
    }

    /**
     * 提取参考内容
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> extractReferenceContents(Map<String, Object> request) {
        Map<String, String> references = new LinkedHashMap<>();
        Object referenceObj = request.get("referenceContents");
        if (referenceObj instanceof Map) {
            Map<?, ?> refMap = (Map<?, ?>) referenceObj;
            for (Map.Entry<?, ?> entry : refMap.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    references.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
        } else if (referenceObj instanceof List) {
            List<?> refList = (List<?>) referenceObj;
            for (Object item : refList) {
                if (item instanceof Map<?, ?>) {
                    Map<?, ?> refItem = (Map<?, ?>) item;
                    Object title = refItem.get("title");
                    Object content = refItem.get("content");
                    if (title != null && content != null) {
                        references.put(String.valueOf(title), String.valueOf(content));
                    }
                }
            }
        } else if (referenceObj instanceof String) {
            references.put("reference", (String) referenceObj);
        }
        return references;
    }
    
    /**
     * 安全发送SSE事件
     */
    private void safeSend(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(event);
        } catch (IllegalStateException ex) {
            logger.warn("SSE已关闭，忽略事件");
        } catch (IOException ex) {
            logger.error("SSE发送失败", ex);
        }
    }
}
