package com.novel.rolling.controller;

import com.novel.dto.AIConfigRequest;
import com.novel.rolling.service.RollingChapterWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 滚动写作模式API
 * 
 * 只有一个接口：传入要写几章，自动完成整个流程
 * 流程：开局 → 评估 → 规划2章 → 写作 → 评估 → 规划 → 循环
 */
@RestController
@RequestMapping("/rolling")
public class RollingWritingController {

    private static final Logger logger = LoggerFactory.getLogger(RollingWritingController.class);

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Autowired
    private RollingChapterWriter chapterWriter;

    /**
     * 滚动写作
     * 
     * POST /api/rolling/{novelId}/write
     * 
     * 请求体：
     * {
     *   "count": 5,           // 要写几章
     *   "provider": "deepseek",
     *   "apiKey": "xxx",
     *   "model": "deepseek-chat",
     *   "baseUrl": "https://api.deepseek.com"  // 可选
     * }
     */
    @PostMapping(value = "/{novelId}/write", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter write(
            @PathVariable Long novelId,
            @RequestBody Map<String, Object> request
    ) {
        SseEmitter emitter = new SseEmitter(1800000L); // 30分钟超时

        int count = 5; // 默认5章
        if (request.containsKey("count")) {
            count = ((Number) request.get("count")).intValue();
        }

        AIConfigRequest aiConfig = extractAIConfig(request);
        final int finalCount = Math.min(count, 20); // 最多20章

        executor.submit(() -> {
            try {
                chapterWriter.writeChapters(novelId, finalCount, aiConfig, emitter);
                emitter.complete();
            } catch (Exception e) {
                logger.error("滚动写作失败", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("写作失败: " + e.getMessage()));
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        });

        return emitter;
    }

    private AIConfigRequest extractAIConfig(Map<String, Object> request) {
        AIConfigRequest config = new AIConfigRequest();
        if (request == null) return config;

        config.setProvider((String) request.get("provider"));
        config.setApiKey((String) request.get("apiKey"));
        config.setModel((String) request.get("model"));
        config.setBaseUrl((String) request.get("baseUrl"));

        return config;
    }
}
