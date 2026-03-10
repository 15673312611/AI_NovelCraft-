package com.novel.controller;

import com.novel.common.Result;
import com.novel.domain.entity.NovelVolume;
import com.novel.dto.AIConfigRequest;
import com.novel.service.VolumeService;
// import com.novel.service.NovelOutlineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

            // 解析AI配置（支持扁平化和嵌套aiConfig两种格式），以便前端携带apikey等信息
            AIConfigRequest aiConfig = new AIConfigRequest();
            if (body != null) {
                if (body.containsKey("provider")) {
                    aiConfig.setProvider((String) body.get("provider"));
                    aiConfig.setApiKey((String) body.get("apiKey"));
                    aiConfig.setModel((String) body.get("model"));
                    aiConfig.setBaseUrl((String) body.get("baseUrl"));
                } else if (body.get("aiConfig") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> aiConfigMap = (Map<String, String>) body.get("aiConfig");
                    aiConfig.setProvider(aiConfigMap.get("provider"));
                    aiConfig.setApiKey(aiConfigMap.get("apiKey"));
                    aiConfig.setModel(aiConfigMap.get("model"));
                    aiConfig.setBaseUrl(aiConfigMap.get("baseUrl"));
                }
            }

            com.novel.domain.entity.AITask task;
            if (aiConfig != null && aiConfig.isValid()) {
                logger.info("✅ 基于确认大纲生成卷规划 - 使用前端提供的AI配置");
                task = volumeService.generateVolumePlansFromConfirmedOutlineAsync(novelId, volumeCount, aiConfig);
            } else {
                logger.warn("⚠️ 基于确认大纲生成卷规划 - 未提供有效AI配置，将使用默认AI配置");
                task = volumeService.generateVolumePlansFromConfirmedOutlineAsync(novelId, volumeCount, null);
            }

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
     * 批量为多个卷生成详细大纲（按需求定制的新接口）
     * POST /volumes/batch-generate-outlines
     * 请求体: { novelId: number, volumeIds: number[], userAdvice?: string }
     * 返回: { async: true, tasks: [{ volumeId, taskId }] }
     */

    /**
     * 查询批量任务进度
     */


//    /**
//     * 开始卷写作会话
//     * POST /volumes/{volumeId}/start-writing
//     */
//    @PostMapping("/{volumeId}/start-writing")
//    public Result<Map<String, Object>> startVolumeWriting(@PathVariable Long volumeId) {
//        try {
//            logger.info("✍️ 开始卷 {} 的写作会话", volumeId);
//
//            Map<String, Object> writingSession = volumeService.startVolumeWriting(volumeId);
//
//            return Result.success(writingSession);
//
//        } catch (Exception e) {
//            logger.error("开始写作会话失败", e);
//            return Result.error("开始失败: " + e.getMessage());
//        }
//    }

//    /**
//     * 生成下一步写作指导
//     * POST /volumes/{volumeId}/next-guidance
//     */
//    @PostMapping("/{volumeId}/next-guidance")
//    public Result<Map<String, Object>> generateNextStepGuidance(
//            @PathVariable Long volumeId,
//            @RequestBody Map<String, Object> request) {
//
//        try {
//            String currentContent = (String) request.get("currentContent");
//            String userInput = (String) request.get("userInput");
//
//            logger.info("💡 为卷 {} 生成下一步指导", volumeId);
//
//            Map<String, Object> guidance = volumeService.generateNextStepGuidance(volumeId, currentContent, userInput);
//
//            return Result.success(guidance);
//
//        } catch (Exception e) {
//            logger.error("生成写作指导失败", e);
//            return Result.error("生成失败: " + e.getMessage());
//        }
//    }

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

}
