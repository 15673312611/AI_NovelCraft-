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
                // 初始化大纲记录（先落库一条空的，状态为DRAFT，便于前端拿到ID并展示进度）
                NovelOutline outline = outlineService.initOutlineRecord(
                    request.getNovelIdAsLong(),
                    request.getBasicIdea(),
                    request.getTargetWordCount(),
                    request.getTargetChapterCount()
                );

                // 先把outlineId发给前端
                emitter.send(SseEmitter.event().name("meta").data("{\"outlineId\":" + outline.getId() + "}"));

                // 开始调用AI并按块写出与写库
                outlineService.streamGenerateOutlineContent(outline, chunk -> {
                    try {
                        emitter.send(SseEmitter.event().name("chunk").data(chunk));
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
     * 确认大纲
     */
    @PutMapping("/{outlineId}/confirm")
    public ResponseEntity<?> confirmOutline(@PathVariable Long outlineId) {
        try {
            NovelOutline outline = outlineService.confirmOutline(outlineId);
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
                outlineService.optimizeOutlineStream(
                    request.getNovelId(),
                    request.getCurrentOutline(),
                    request.getSuggestion(),
                    chunk -> {
                        try {
                            emitter.send(SseEmitter.event().name("chunk").data(chunk));
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
    }

    // 请求DTO类
    public static class OutlineGenerationRequest {
        private String novelId; // 改为String类型支持前端传入
        private String basicIdea;
        private Integer targetWordCount;
        private Integer targetChapterCount;

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