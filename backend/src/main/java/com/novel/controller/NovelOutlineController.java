package com.novel.controller;

import com.novel.domain.entity.NovelOutline;
import com.novel.service.NovelOutlineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.validation.Valid;
import java.util.Optional;

/**
 * 小说大纲控制器
 *
 * @author Novel Creation System
 * @version 1.0.0
 * @since 2024-01-01
 */
@RestController
@RequestMapping("/outline")
@CrossOrigin
public class NovelOutlineController {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(NovelOutlineController.class);

    @Autowired
    private NovelOutlineService outlineService;

    /**
     * 生成初始大纲
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generateOutline(@RequestBody @Valid OutlineGenerationRequest request) {
        try {
            System.out.println("Debug - Received request: " + request);
            System.out.println("Debug - novelId: " + request.getNovelId());
            System.out.println("Debug - basicIdea: " + request.getBasicIdea());
            System.out.println("Debug - targetWordCount: " + request.getTargetWordCount());
            System.out.println("Debug - targetChapterCount: " + request.getTargetChapterCount());

            NovelOutline outline = outlineService.generateInitialOutline(
                request.getNovelIdAsLong(),
                request.getBasicIdea(),
                request.getTargetWordCount(),
                request.getTargetChapterCount()
            );
            return ResponseEntity.ok(outline);
        } catch (Exception e) {
            System.err.println("Debug - Error in generateOutline: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * 流式生成大纲（SSE）
     * 说明：直接按块把AI返回内容写出，同时边生成边累加写入 plotStructure，完成后置为 DRAFT
     */
    @PostMapping(value = "/generate-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateOutlineStream(@RequestBody @Valid OutlineGenerationRequest request) {
        SseEmitter emitter = new SseEmitter(0L); // 不超时
        new Thread(() -> {
            try {
                // 提取AI配置
                com.novel.dto.AIConfigRequest aiConfig = request.getAiConfig();
                
                // 初始化大纲记录（先落库一条空的，状态为DRAFT，便于前端拿到ID并展示进度）
                NovelOutline outline = outlineService.initOutlineRecord(
                    request.getNovelIdAsLong(),
                    request.getBasicIdea(),
                    request.getTargetWordCount(),
                    request.getTargetChapterCount()
                );

                // 先把outlineId发给前端
                emitter.send(SseEmitter.event().name("meta").data("{\"outlineId\":" + outline.getId() + "}"));

                // 开始调用AI并按块写出与写库（支持模板和字数限制）
                Long templateId = request.getTemplateId();
                Integer outlineWordLimit = request.getOutlineWordLimit() != null ? request.getOutlineWordLimit() : 2000;
                outlineService.streamGenerateOutlineContent(outline, aiConfig, templateId, outlineWordLimit, chunk -> {
                    try {
                        // 直接发送纯文本数据，不带event名称
                        emitter.send(chunk);
                    } catch (Exception sendEx) {
                        throw new RuntimeException(sendEx);
                    }
                });

                // 完成
                emitter.send(SseEmitter.event().name("done").data("completed"));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        }).start();
        return emitter;
    }

    /**
     * 修改大纲
     */
    @PutMapping("/{outlineId}/revise")
    public ResponseEntity<?> reviseOutline(@PathVariable Long outlineId, 
                                         @RequestBody @Valid OutlineRevisionRequest request) {
        try {
            NovelOutline outline = outlineService.reviseOutline(outlineId, request.getFeedback());
            return ResponseEntity.ok(outline);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * 确认大纲（支持前端传递AI配置）
     */
    @PutMapping("/{outlineId}/confirm")
    public ResponseEntity<?> confirmOutline(
            @PathVariable Long outlineId,
            @RequestBody(required = false) java.util.Map<String, Object> requestBody) {
        try {
            // 尝试从请求体中提取AI配置
            com.novel.dto.AIConfigRequest aiConfig = null;
            if (requestBody != null && !requestBody.isEmpty()) {
                try {
                    String provider = (String) requestBody.get("provider");
                    String apiKey = (String) requestBody.get("apiKey");
                    String model = (String) requestBody.get("model");
                    String baseUrl = (String) requestBody.get("baseUrl");
                    
                    if (provider != null && apiKey != null && model != null) {
                        aiConfig = new com.novel.dto.AIConfigRequest(provider, apiKey, model, baseUrl);
                        logger.info("确认大纲时接收到AI配置: provider={}, model={}", provider, model);
                    }
                } catch (Exception e) {
                    logger.warn("解析AI配置失败: {}", e.getMessage());
                }
            }
            
            NovelOutline outline = outlineService.confirmOutline(outlineId, aiConfig);
            return ResponseEntity.ok(outline);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * 根据小说ID获取大纲
     */
    @GetMapping("/novel/{novelId}")
    public ResponseEntity<?> getOutlineByNovelId(@PathVariable Long novelId) {
        try {
            Optional<NovelOutline> outline = outlineService.findByNovelId(novelId);
            if (outline.isPresent()) {
                return ResponseEntity.ok(outline.get());
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * 更新小说大纲内容（手动编辑）
     */
    @PutMapping("/novel/{novelId}")
    public ResponseEntity<?> updateOutlineByNovelId(
            @PathVariable Long novelId,
            @RequestBody UpdateOutlineRequest request) {
        try {
            logger.info("更新小说ID={}的大纲内容", novelId);
            
            if (request.getOutline() == null || request.getOutline().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(new ErrorResponse("大纲内容不能为空"));
            }
            
            NovelOutline outline = outlineService.updateOutlineContent(novelId, request.getOutline());
            logger.info("大纲更新成功，ID={}", outline.getId());
            
            return ResponseEntity.ok(outline);
        } catch (Exception e) {
            logger.error("更新大纲失败", e);
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * 根据ID获取大纲详情
     */
    @GetMapping("/{outlineId}")
    public ResponseEntity<?> getOutlineById(@PathVariable Long outlineId) {
        try {
            NovelOutline outline = outlineService.getById(outlineId);
            if (outline != null) {
                return ResponseEntity.ok(outline);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * AI优化小说大纲（流式）
     */
    @PostMapping(value = "/optimize-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter optimizeOutlineStream(@RequestBody OutlineOptimizationRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        new Thread(() -> {
            try {
                // 提取AI配置
                com.novel.dto.AIConfigRequest aiConfig = request.getAiConfig();
                
                outlineService.optimizeOutlineStream(
                    request.getNovelId(),
                    request.getCurrentOutline(),
                    request.getSuggestion(),
                    aiConfig,
                    chunk -> {
                        try {
                            // 直接发送纯文本数据，不带event名称
                            emitter.send(chunk);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                );
                emitter.send(SseEmitter.event().name("done").data("completed"));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        }).start();
        return emitter;
    }

    // 大纲优化请求DTO
    public static class OutlineOptimizationRequest {
        private Long novelId;
        private String currentOutline;
        private String suggestion;
        
        // AI配置字段
        private String provider;
        private String apiKey;
        private String model;
        private String baseUrl;

        public Long getNovelId() {
            return novelId;
        }

        public void setNovelId(Long novelId) {
            this.novelId = novelId;
        }

        public String getCurrentOutline() {
            return currentOutline;
        }

        public void setCurrentOutline(String currentOutline) {
            this.currentOutline = currentOutline;
        }

        public String getSuggestion() {
            return suggestion;
        }

        public void setSuggestion(String suggestion) {
            this.suggestion = suggestion;
        }
        
        // AI配置的getters和setters
        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
        
        // 获取AIConfigRequest对象
        public com.novel.dto.AIConfigRequest getAiConfig() {
            if (provider != null && apiKey != null && model != null) {
                return new com.novel.dto.AIConfigRequest(provider, apiKey, model, baseUrl);
            }
            return null;
        }
    }

    // 请求DTO类
    public static class OutlineGenerationRequest {
        private String novelId; // 改为String类型支持前端传入
        private String basicIdea;
        private Integer targetWordCount;
        private Integer targetChapterCount;

        // AI配置字段
        private String provider;
        private String apiKey;
        private String model;
        private String baseUrl;

        // 模板ID（可选，如果提供则使用模板生成大纲）
        private Long templateId;

        // 大纲字数限制（默认2000字）
        private Integer outlineWordLimit;

        // 预期卷数（用于生成卷大纲）
        private Integer volumeCount;

        // Getters and Setters
        public String getNovelId() {
            return novelId;
        }

        public void setNovelId(String novelId) {
            this.novelId = novelId;
        }
        
        // 获取Long类型的novelId
        public Long getNovelIdAsLong() {
            try {
                return novelId != null ? Long.parseLong(novelId) : null;
            } catch (NumberFormatException e) {
                throw new RuntimeException("无效的小说ID格式: " + novelId);
            }
        }

        public String getBasicIdea() {
            return basicIdea;
        }

        public void setBasicIdea(String basicIdea) {
            this.basicIdea = basicIdea;
        }

        public Integer getTargetWordCount() {
            return targetWordCount;
        }

        public void setTargetWordCount(Integer targetWordCount) {
            this.targetWordCount = targetWordCount;
        }

        public Integer getTargetChapterCount() {
            return targetChapterCount;
        }

        public void setTargetChapterCount(Integer targetChapterCount) {
            this.targetChapterCount = targetChapterCount;
        }
        
        // AI配置的getters和setters
        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
        
        public Long getTemplateId() {
            return templateId;
        }
        
        public void setTemplateId(Long templateId) {
            this.templateId = templateId;
        }

        public Integer getOutlineWordLimit() {
            return outlineWordLimit;
        }

        public void setOutlineWordLimit(Integer outlineWordLimit) {
            this.outlineWordLimit = outlineWordLimit;
        }

        public Integer getVolumeCount() {
            return volumeCount;
        }

        public void setVolumeCount(Integer volumeCount) {
            this.volumeCount = volumeCount;
        }

        // 获取AIConfigRequest对象
        public com.novel.dto.AIConfigRequest getAiConfig() {
            if (provider != null && apiKey != null && model != null) {
                return new com.novel.dto.AIConfigRequest(provider, apiKey, model, baseUrl);
            }
            return null;
        }
    }

    public static class OutlineRevisionRequest {
        private String feedback;

        public String getFeedback() {
            return feedback;
        }

        public void setFeedback(String feedback) {
            this.feedback = feedback;
        }
    }

    public static class UpdateOutlineRequest {
        private String outline;

        public String getOutline() {
            return outline;
        }

        public void setOutline(String outline) {
            this.outline = outline;
        }
    }

    public static class ErrorResponse {
        private String message;

        public ErrorResponse(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}

