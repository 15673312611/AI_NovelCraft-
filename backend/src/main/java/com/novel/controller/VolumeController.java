package com.novel.controller;

import com.novel.common.ApiResponse;
import com.novel.common.Result;
import com.novel.config.GlobalExceptionHandler.BusinessException;
import com.novel.domain.entity.NovelVolume;
import com.novel.dto.AIConfigRequest;
import com.novel.service.VolumeService;
import com.novel.service.NovelVolumeService;
// import com.novel.service.NovelOutlineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 小说卷管理控制器
 * 基于卷的创作系统API接口
 */
@RestController
@RequestMapping("/volumes")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}, allowCredentials = "true")
public class VolumeController {
    // TODO: 与 NovelVolumeController 共用 /volumes 前缀，存在路由与职责重叠；建议合并或调整路径避免冲突


    private static final Logger logger = LoggerFactory.getLogger(VolumeController.class);

    @Autowired
    private VolumeService volumeService;

    // 保留注入占位，暂未在本控制器使用，后续可能扩展
    // @Autowired
    // private NovelOutlineService novelOutlineService;



    /**
     * 基于超级大纲生成卷规划（推荐方式）
     * POST /volumes/{novelId}/generate-from-super-outline
     */
    @PostMapping("/{novelId}/generate-from-super-outline")
    public Result<List<NovelVolume>> generateVolumesFromSuperOutline(
            @PathVariable Long novelId,
            @RequestBody Map<String, Object> request) {
        return Result.error("该接口已停用，请改用 /volumes/{novelId}/generate-from-outline");
    }


    /**
     * 基于“已确认”大纲生成卷规划（异步触发）
     * 前端在无法拿到 outlineId 时，可直接调用此接口
     * POST /volumes/{novelId}/generate-from-outline
     * body: { volumeCount?: number }
     */
    @PostMapping("/{novelId}/generate-from-outline")
    public Result<Map<String, Object>> generateVolumesFromConfirmedOutline(
            @PathVariable Long novelId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            Integer volumeCount = null;
            if (body != null && body.get("volumeCount") instanceof Number) {
                volumeCount = ((Number) body.get("volumeCount")).intValue();
            }
            if (volumeCount == null) {
                // 无入参时由 VolumeService 内部按目标章字数估算
                volumeCount = 0; // 0 表示让服务自行估算
            }

            com.novel.domain.entity.AITask task = volumeService.generateVolumePlansFromConfirmedOutlineAsync(novelId, volumeCount);
            Map<String, Object> resp = new HashMap<>();
            resp.put("async", true);
            resp.put("taskId", task != null ? task.getId() : null);
            resp.put("novelId", novelId);
            resp.put("message", "已触发基于确认大纲的卷规划生成");
            return Result.success(resp);
        } catch (Exception e) {
            logger.error("触发基于确认大纲的卷规划失败", e);
            return Result.error("触发失败: " + e.getMessage());
        }
    }




    // 注意：获取小说的所有卷在 NovelVolumeController 暴露为 /volumes/novel/{novelId}
    // 这里移除 /volumes/{novelId} 以避免与 /volumes/{volumeId} 冲突

    /**
     * 为指定卷生成详细大纲（异步任务模式）
     * POST /volumes/{volumeId}/generate-outline-async
     */
    @PostMapping("/{volumeId}/generate-outline-async")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateVolumeOutlineAsync(
            @PathVariable Long volumeId,
            @RequestBody(required = false) Map<String, Object> request) {

        logger.info("📋 接收卷大纲异步生成请求: volumeId={}", volumeId);

        try {
            String userAdvice = null;
            AIConfigRequest aiConfig = new AIConfigRequest();

            if (request != null) {
                Object adviceObj = request.get("userAdvice");
                if (adviceObj instanceof String) {
                    userAdvice = (String) adviceObj;
                }
                
                // 解析AI配置（前端withAIConfig是扁平化的，直接从根级别读取）
                if (request.containsKey("provider")) {
                    aiConfig.setProvider((String) request.get("provider"));
                    aiConfig.setApiKey((String) request.get("apiKey"));
                    aiConfig.setModel((String) request.get("model"));
                    aiConfig.setBaseUrl((String) request.get("baseUrl"));
                    
                    logger.info("✅ 卷大纲异步生成 - 收到AI配置: provider={}, model={}", 
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
            }
            
            if (!aiConfig.isValid()) {
                logger.error("❌ 卷大纲异步生成 - AI配置无效: volumeId={}, request={}", volumeId, request);
                return ResponseEntity.badRequest().body(
                    ApiResponse.error("AI配置无效，请先在设置页面配置AI服务")
                );
            }

            // 创建异步任务
            Map<String, Object> result = volumeService.generateVolumeOutlineAsync(volumeId, userAdvice, aiConfig);

            Map<String, Object> response = new HashMap<>();
            response.put("asyncTask", true);
            response.put("taskId", result.get("taskId"));
            response.put("volumeId", volumeId);
            response.put("message", "卷大纲生成任务已创建");

            logger.info("✅ 卷 {} 异步大纲生成任务创建成功，任务ID: {}", volumeId, result.get("taskId"));
            return ResponseEntity.ok(ApiResponse.success("卷大纲生成任务已创建", response));

        } catch (Exception e) {
            logger.error("❌ 创建卷大纲异步任务失败: volumeId={}", volumeId, e);

            // 异步任务创建失败，直接返回错误
            logger.warn("⚠️ 异步任务创建失败: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.error("创建异步任务失败: " + e.getMessage()));
        }
    }


    /**
     * 流式生成单个卷的详细大纲（SSE）
     * POST /volumes/{volumeId}/generate-outline-stream
     * 请求体: { userAdvice?: string }
     * 返回: SSE流
     */
    @PostMapping(value = "/{volumeId}/generate-outline-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateVolumeOutlineStream(
            @PathVariable Long volumeId,
            @RequestBody(required = false) Map<String, Object> request) {
        
        logger.info("📋 接收卷大纲流式生成请求: volumeId={}", volumeId);
        
        SseEmitter emitter = new SseEmitter(0L); // 不超时
        
        new Thread(() -> {
            try {
                String userAdvice = null;
                AIConfigRequest aiConfig = new AIConfigRequest();
                
                if (request != null) {
                    if (request.get("userAdvice") instanceof String) {
                        userAdvice = (String) request.get("userAdvice");
                    }
                    
                    // 解析AI配置（前端withAIConfig是扁平化的，直接从根级别读取）
                    if (request.containsKey("provider")) {
                        aiConfig.setProvider((String) request.get("provider"));
                        aiConfig.setApiKey((String) request.get("apiKey"));
                        aiConfig.setModel((String) request.get("model"));
                        aiConfig.setBaseUrl((String) request.get("baseUrl"));
                        
                        logger.info("✅ 卷大纲流式生成 - 收到AI配置: provider={}, model={}", 
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
                }
                
                // 验证AI配置
                if (!aiConfig.isValid()) {
                    logger.error("❌ 卷大纲流式生成 - AI配置无效: volumeId={}, request={}", volumeId, request);
                    emitter.send(SseEmitter.event().name("error").data("AI配置无效，请先在设置页面配置AI服务"));
                    emitter.completeWithError(new RuntimeException("AI配置无效"));
                    return;
                }
                
                // 调用VolumeService的流式生成方法
                volumeService.streamGenerateVolumeOutline(volumeId, userAdvice, aiConfig, chunk -> {
                    try {
                        // 直接发送纯文本数据，不带event名称
                        emitter.send(chunk);
                    } catch (Exception e) {
                        logger.error("发送SSE chunk失败", e);
                        throw new RuntimeException(e);
                    }
                });
                
                // 完成
                emitter.send(SseEmitter.event().name("done").data("completed"));
                emitter.complete();
                logger.info("✅ 卷 {} 流式大纲生成完成", volumeId);
                
            } catch (Exception e) {
                logger.error("❌ 流式生成卷大纲失败: volumeId={}", volumeId, e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        }).start();
        
        return emitter;
    }

    /**
     * 批量为多个卷生成详细大纲（按需求定制的新接口）
     * POST /volumes/batch-generate-outlines
     * 请求体: { novelId: number, volumeIds: number[], userAdvice?: string }
     * 返回: { async: true, tasks: [{ volumeId, taskId }] }
     */

    /**
     * 查询批量任务进度
     */

    /**
     * 获取卷大纲（不生成，仅获取已有的）
     * GET /volumes/{volumeId}/outline
     */
    @GetMapping("/{volumeId}/outline")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVolumeOutline(@PathVariable Long volumeId) {

        logger.info("📋 获取卷大纲: volumeId={}", volumeId);

        try {
            // 从VolumeService获取卷详情
            Map<String, Object> detail = volumeService.getVolumeDetail(volumeId);
            NovelVolume volume = (NovelVolume) detail.get("volume");

            Map<String, Object> response = new HashMap<>();
            response.put("volumeId", volumeId);
            response.put("hasOutline", volume.getContentOutline() != null && !volume.getContentOutline().trim().isEmpty());

            if (response.get("hasOutline").equals(true)) {
                // 解析现有大纲
                Map<String, Object> parsedOutline = parseExistingOutlineForResponse(volume.getContentOutline());
                response.put("outline", parsedOutline);
                response.put("outlineText", volume.getContentOutline());
            } else {
                response.put("outline", null);
                response.put("outlineText", "");
            }

            response.put("volume", volume);
            response.put("lastModified", volume.getLastModifiedByAi());

            return ResponseEntity.ok(ApiResponse.success("获取卷大纲成功", response));

        } catch (Exception e) {
            logger.error("❌ 获取卷大纲失败: volumeId={}", volumeId, e);
            throw new BusinessException("获取卷大纲失败: " + e.getMessage());
        }
    }

    /**
     * 开始卷写作会话
     * POST /volumes/{volumeId}/start-writing
     */
    @PostMapping("/{volumeId}/start-writing")
    public Result<Map<String, Object>> startVolumeWriting(@PathVariable Long volumeId) {
        try {
            logger.info("✍️ 开始卷 {} 的写作会话", volumeId);

            Map<String, Object> writingSession = volumeService.startVolumeWriting(volumeId);

            return Result.success(writingSession);

        } catch (Exception e) {
            logger.error("开始写作会话失败", e);
            return Result.error("开始失败: " + e.getMessage());
        }
    }

    /**
     * 生成下一步写作指导
     * POST /volumes/{volumeId}/next-guidance
     */
    @PostMapping("/{volumeId}/next-guidance")
    public Result<Map<String, Object>> generateNextStepGuidance(
            @PathVariable Long volumeId,
            @RequestBody Map<String, Object> request) {

        try {
            String currentContent = (String) request.get("currentContent");
            String userInput = (String) request.get("userInput");

            logger.info("💡 为卷 {} 生成下一步指导", volumeId);

            Map<String, Object> guidance = volumeService.generateNextStepGuidance(volumeId, currentContent, userInput);

            return Result.success(guidance);

        } catch (Exception e) {
            logger.error("生成写作指导失败", e);
            return Result.error("生成失败: " + e.getMessage());
        }
    }

    /**
     * AI优化卷大纲（流式）
     * POST /volumes/{volumeId}/optimize-outline-stream
     */
    @PostMapping(value = "/{volumeId}/optimize-outline-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter optimizeVolumeOutlineStream(
            @PathVariable Long volumeId,
            @RequestBody Map<String, Object> request) {

        SseEmitter emitter = new SseEmitter(0L);
        new Thread(() -> {
            try {
                String currentOutline = (String) request.get("currentOutline");
                String suggestion = (String) request.get("suggestion");
                @SuppressWarnings("unchecked")
                Map<String, Object> volumeInfo = (Map<String, Object>) request.get("volumeInfo");

                // 提取AI配置
                com.novel.dto.AIConfigRequest aiConfig = null;
                try {
                    String provider = (String) request.get("provider");
                    String apiKey = (String) request.get("apiKey");
                    String model = (String) request.get("model");
                    String baseUrl = (String) request.get("baseUrl");
                    
                    if (provider != null && apiKey != null && model != null) {
                        aiConfig = new com.novel.dto.AIConfigRequest(provider, apiKey, model, baseUrl);
                    }
                } catch (Exception e) {
                    logger.warn("解析AI配置失败: {}", e.getMessage());
                }

                logger.info("🎨 流式优化卷 {} 的大纲", volumeId);

                volumeService.optimizeVolumeOutlineStream(
                    volumeId, 
                    currentOutline, 
                    suggestion,
                    volumeInfo,
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
                logger.error("优化卷大纲失败", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        }).start();
        return emitter;
    }

    /**
     * 根据用户需求修改卷蓝图（流式，考虑前后卷上下文）
     * POST /volumes/{volumeId}/modify-blueprint-stream
     * 请求体: { userRequirement: string, provider: string, apiKey: string, model: string, baseUrl?: string }
     * 返回: SSE流
     */
    @PostMapping(value = "/{volumeId}/modify-blueprint-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter modifyVolumeBlueprintStream(
            @PathVariable Long volumeId,
            @RequestBody Map<String, Object> request) {
        
        logger.info("🔧 接收卷蓝图修改请求: volumeId={}", volumeId);
        
        SseEmitter emitter = new SseEmitter(0L); // 不超时
        
        new Thread(() -> {
            try {
                // 解析用户需求
                String userRequirement = (String) request.get("userRequirement");
                if (userRequirement == null || userRequirement.trim().isEmpty()) {
                    emitter.send(SseEmitter.event().name("error").data("用户修改需求不能为空"));
                    emitter.completeWithError(new RuntimeException("用户修改需求不能为空"));
                    return;
                }
                
                // 解析AI配置
                AIConfigRequest aiConfig = new AIConfigRequest();
                if (request.containsKey("provider")) {
                    aiConfig.setProvider((String) request.get("provider"));
                    aiConfig.setApiKey((String) request.get("apiKey"));
                    aiConfig.setModel((String) request.get("model"));
                    aiConfig.setBaseUrl((String) request.get("baseUrl"));
                    
                    logger.info("✅ 卷蓝图修改 - 收到AI配置: provider={}, model={}", 
                        aiConfig.getProvider(), aiConfig.getModel());
                } else if (request.get("aiConfig") instanceof Map) {
                    // 兼容嵌套格式
                    @SuppressWarnings("unchecked")
                    Map<String, String> aiConfigMap = (Map<String, String>) request.get("aiConfig");
                    aiConfig.setProvider(aiConfigMap.get("provider"));
                    aiConfig.setApiKey(aiConfigMap.get("apiKey"));
                    aiConfig.setModel(aiConfigMap.get("model"));
                    aiConfig.setBaseUrl(aiConfigMap.get("baseUrl"));
                }
                
                // 验证AI配置
                if (!aiConfig.isValid()) {
                    logger.error("❌ 卷蓝图修改 - AI配置无效: volumeId={}, request={}", volumeId, request);
                    emitter.send(SseEmitter.event().name("error").data("AI配置无效，请先在设置页面配置AI服务"));
                    emitter.completeWithError(new RuntimeException("AI配置无效"));
                    return;
                }
                
                // 调用VolumeService的修改方法
                volumeService.modifyVolumeBlueprintWithContext(volumeId, userRequirement, aiConfig, chunk -> {
                    try {
                        // 直接发送纯文本数据，不带event名称
                        emitter.send(chunk);
                    } catch (Exception e) {
                        logger.error("发送SSE chunk失败", e);
                        throw new RuntimeException(e);
                    }
                });
                
                // 完成
                emitter.send(SseEmitter.event().name("done").data("completed"));
                emitter.complete();
                logger.info("✅ 卷 {} 蓝图修改完成", volumeId);
                
            } catch (Exception e) {
                logger.error("❌ 修改卷蓝图失败: volumeId={}", volumeId, e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        }).start();
        
        return emitter;
    }

    /**
     * 更新卷信息
     * PUT /volumes/{volumeId}
     */
    @PutMapping("/{volumeId}")
    public Result<NovelVolume> updateVolume(
            @PathVariable Long volumeId,
            @RequestBody Map<String, Object> request) {

        try {
            logger.info("📝 更新卷 {} 的信息", volumeId);
            
            NovelVolume volume = volumeService.getVolumeById(volumeId);
            if (volume == null) {
                return Result.error("卷不存在");
            }

            // 更新大纲
            if (request.containsKey("contentOutline")) {
                String contentOutline = (String) request.get("contentOutline");
                volume.setContentOutline(contentOutline);
                logger.info("更新卷大纲，长度: {}", contentOutline != null ? contentOutline.length() : 0);
            }

            // 更新其他字段
            if (request.containsKey("title")) {
                volume.setTitle((String) request.get("title"));
            }
            if (request.containsKey("description")) {
                volume.setDescription((String) request.get("description"));
            }

            volumeService.updateVolume(volume);
            logger.info("✅ 卷 {} 更新成功", volumeId);

            return Result.success(volume);

        } catch (Exception e) {
            logger.error("更新卷信息失败", e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 更新卷的实际字数
     * PUT /volumes/{volumeId}/word-count
     */
    @PutMapping("/{volumeId}/word-count")
    public Result<String> updateActualWordCount(
            @PathVariable Long volumeId,
            @RequestBody Map<String, Object> request) {

        try {
            Integer actualWordCount = (Integer) request.get("actualWordCount");

            if (actualWordCount == null || actualWordCount < 0) {
                return Result.error("字数必须为非负整数");
            }

            volumeService.updateActualWordCount(volumeId, actualWordCount);

            return Result.success("字数更新成功");

        } catch (Exception e) {
            logger.error("更新字数失败", e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除卷
     * DELETE /volumes/{volumeId}
     */
    @DeleteMapping("/{volumeId}")
    public Result<String> deleteVolume(@PathVariable Long volumeId) {
        try {
            volumeService.deleteVolume(volumeId);
            return Result.success("删除成功");
        } catch (Exception e) {
            logger.error("删除卷失败", e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }


    /**
     * 获取卷详情
     * GET /volumes/detail/{volumeId}
     */
    @GetMapping("/detail/{volumeId}")
    public Result<Map<String, Object>> getVolumeDetail(@PathVariable Long volumeId) {
        try {
            Map<String, Object> detail = volumeService.getVolumeDetail(volumeId);
            return Result.success(detail);

        } catch (Exception e) {
            logger.error("获取卷详情失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取小说卷统计信息
     * GET /volumes/{novelId}/stats
     */
    @GetMapping("/{novelId}/stats")
    public Result<Map<String, Object>> getVolumeStats(@PathVariable Long novelId) {
        try {
            List<NovelVolume> volumes = volumeService.getVolumesByNovelId(novelId);

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalVolumes", volumes.size());
            stats.put("completedVolumes", volumes.stream().mapToInt(v -> v.isCompleted() ? 1 : 0).sum());
            stats.put("inProgressVolumes", volumes.stream().mapToInt(v -> v.isInProgress() ? 1 : 0).sum());
            stats.put("totalEstimatedWords", volumes.stream().mapToInt(v -> v.getEstimatedWordCount() != null ? v.getEstimatedWordCount() : 0).sum());
            stats.put("totalActualWords", volumes.stream().mapToInt(v -> v.getActualWordCount() != null ? v.getActualWordCount() : 0).sum());

            double avgProgress = volumes.stream().mapToDouble(NovelVolume::getProgress).average().orElse(0.0);
            stats.put("averageProgress", Math.round(avgProgress * 100.0) / 100.0);

            return Result.success(stats);

        } catch (Exception e) {
            logger.error("获取统计信息失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 为响应解析现有大纲文本
     */
    private Map<String, Object> parseExistingOutlineForResponse(String outlineText) {
        Map<String, Object> outline = new HashMap<>();

        if (outlineText == null || outlineText.trim().isEmpty()) {
            return outline;
        }

        outline.put("rawOutline", outlineText);
        outline.put("isExisting", true);
        outline.put("summary", extractSummaryFromOutline(outlineText));

        return outline;
    }

    /**
     * 从大纲文本中提取摘要信息
     */
    private String extractSummaryFromOutline(String outlineText) {
        if (outlineText == null || outlineText.trim().isEmpty()) {
            return "暂无大纲内容";
        }

        // 提取前200个字符作为摘要
        String summary = outlineText.length() > 200 ?
            outlineText.substring(0, 200) + "..." :
            outlineText;

        // 移除过多的换行符
        summary = summary.replaceAll("\n{3,}", "\n\n");

        return summary;
    }
}