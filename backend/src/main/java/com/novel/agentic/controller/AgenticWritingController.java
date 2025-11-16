package com.novel.agentic.controller;

import com.novel.agentic.service.AgenticChapterWriter;
import com.novel.dto.AIConfigRequest;
import com.novel.service.ChapterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    public SseEmitter generateChaptersStream(@RequestBody Map<String, Object> request) {
        
        Long novelId = ((Number) request.get("novelId")).longValue();

        Integer startChapter = null;
        if (request.containsKey("startChapter") && request.get("startChapter") != null) {
            startChapter = ((Number) request.get("startChapter")).intValue();
        } else {
            startChapter = chapterService.getNextChapterNumber(novelId);
            logger.info("📌 未显式指定起始章节，自动从数据库最近一章推算下一章: {}", startChapter);
        }
        if (startChapter == null || startChapter < 1) {
            startChapter = 1;
        }
        Integer count = request.containsKey("count") ? 
            ((Number) request.get("count")).intValue() : 1;
        String userAdjustment = (String) request.get("userAdjustment");
        String stylePromptFile = (String) request.get("stylePromptFile");
        Map<String, String> referenceContents = extractReferenceContents(request);
        
        logger.info("📝 代理式AI写作请求: novelId={}, 起始章节={}, 数量={}, 风格提示词={}", 
            novelId, startChapter, count, stylePromptFile != null ? stylePromptFile : "默认");
        
        SseEmitter emitter = new SseEmitter(0L);
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

        heartbeat.scheduleAtFixedRate(() -> safeSend(emitter,
                SseEmitter.event().name("keepalive").data("💓")),
            0, 20, TimeUnit.SECONDS);

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
        
        // 提取AI配置
        AIConfigRequest aiConfig = extractAIConfig(request);
        
        // 创建final副本供lambda使用
        final Integer finalStartChapter = startChapter;
        final Integer finalCount = count;
        final String finalUserAdjustment = userAdjustment;
        final AIConfigRequest finalAiConfig = aiConfig;
        final String finalStylePromptFile = stylePromptFile;
        final Map<String, String> finalReferenceContents = referenceContents;
        
        // 异步执行
        CompletableFuture.runAsync(() -> {
            try {
                if (finalCount == 1) {
                    chapterWriter.generateChapter(novelId, finalStartChapter, finalUserAdjustment, finalAiConfig, finalStylePromptFile, finalReferenceContents, emitter);
                } else {
                    chapterWriter.generateMultipleChapters(novelId, finalStartChapter, finalCount, finalAiConfig, finalStylePromptFile, finalReferenceContents, emitter);
                }
                
                stopHeartbeat.run();
                emitter.complete();
                
            } catch (Exception e) {
                logger.error("代理式AI写作失败", e);
                try {
                    safeSend(emitter, SseEmitter.event()
                        .name("error")
                        .data("生成失败: " + e.getMessage()));
                    emitter.completeWithError(e);
                } catch (Exception ex) {
                    logger.error("发送错误事件失败", ex);
                } finally {
                    stopHeartbeat.run();
                }
            }
        });
        
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
     * 提取AI配置
     */
    @SuppressWarnings("unchecked")
    private AIConfigRequest extractAIConfig(Map<String, Object> request) {
        Object aiConfigObj = request.get("aiConfig");
        
        if (aiConfigObj instanceof Map) {
            Map<String, Object> configMap = (Map<String, Object>) aiConfigObj;
            
            AIConfigRequest config = new AIConfigRequest();
            
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
            // 注意：AIConfigRequest目前不支持temperature和maxTokens，这些参数会被忽略
            // 如果需要支持，需要在AIConfigRequest中添加相应字段
            
            return config;
        }
        
        return new AIConfigRequest(); // 使用默认配置
    }

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
                if (item instanceof Map) {
                    Map<String, Object> refItem = (Map<String, Object>) item;
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
