package com.novel.controller;

import com.novel.domain.entity.NovelVolume;
import com.novel.dto.AIConfigRequest;
import com.novel.service.NovelVolumeService;
import com.novel.service.VolumeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 小说卷控制器
 * 负责卷列表查询和批量操作
 * 
 * @author Novel Creation System
 * @version 1.0.0
 * @since 2024-01-01
 */
@RestController
@RequestMapping("/volumes")
@CrossOrigin
public class NovelVolumeController {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(NovelVolumeController.class);

    @Autowired
    private VolumeService volumeService;

    @Autowired
    private NovelVolumeService novelVolumeService;

    /**
     * 获取小说的所有卷
     * 前端调用：novelVolumeService.getVolumesByNovelId()
     */
    @GetMapping("/novel/{novelId}")
    public ResponseEntity<?> getVolumesByNovelId(@PathVariable Long novelId) {
        try {
            List<NovelVolume> volumes = novelVolumeService.getVolumesByNovelId(novelId);
            return ResponseEntity.ok(volumes);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * 批量为所有卷生成详细大纲（异步，每卷一个任务）
     * 前端调用：VolumeManagementPage 的批量生成功能
     * 支持两种模式：
     * - 按原主题（userAdvice 为空）
     * - 按建议生成（提供 userAdvice）
     */
    @PostMapping("/batch-generate-outlines")
    public ResponseEntity<?> batchGenerateOutlines(@RequestBody @Valid Map<String, Object> requestMap) {
        try {
            Long novelId = ((Number) requestMap.get("novelId")).longValue();
            String userAdvice = (String) requestMap.get("userAdvice");
            
            // 解析AI配置（前端withAIConfig是扁平化的，直接从根级别读取）
            AIConfigRequest aiConfig = new AIConfigRequest();
            if (requestMap.containsKey("provider")) {
                aiConfig.setProvider((String) requestMap.get("provider"));
                aiConfig.setApiKey((String) requestMap.get("apiKey"));
                aiConfig.setModel((String) requestMap.get("model"));
                aiConfig.setBaseUrl((String) requestMap.get("baseUrl"));
                
                logger.info("✅ 批量生成卷大纲 - 收到AI配置: provider={}, model={}", 
                    aiConfig.getProvider(), aiConfig.getModel());
            } else if (requestMap.get("aiConfig") instanceof Map) {
                // 兼容旧的嵌套格式
                @SuppressWarnings("unchecked")
                Map<String, String> aiConfigMap = (Map<String, String>) requestMap.get("aiConfig");
                aiConfig.setProvider(aiConfigMap.get("provider"));
                aiConfig.setApiKey(aiConfigMap.get("apiKey"));
                aiConfig.setModel(aiConfigMap.get("model"));
                aiConfig.setBaseUrl(aiConfigMap.get("baseUrl"));
            }
            
            if (!aiConfig.isValid()) {
                logger.error("❌ 批量生成卷大纲 - AI配置无效: {}", requestMap);
                return ResponseEntity.badRequest().body(new ErrorResponse("AI配置无效，请先在设置页面配置AI服务"));
            }

            // 如果未传具体卷ID，默认取该小说的全部卷
            java.util.List<com.novel.domain.entity.NovelVolume> targetVolumes;
            Object volumeIdsObj = requestMap.get("volumeIds");
            if (volumeIdsObj == null || (volumeIdsObj instanceof java.util.List && ((java.util.List<?>) volumeIdsObj).isEmpty())) {
                targetVolumes = novelVolumeService.getVolumesByNovelId(novelId);
            } else {
                targetVolumes = new java.util.ArrayList<>();
                @SuppressWarnings("unchecked")
                java.util.List<Number> volumeIds = (java.util.List<Number>) volumeIdsObj;
                for (Number vid : volumeIds) {
                    com.novel.domain.entity.NovelVolume v = novelVolumeService.getById(vid.longValue());
                    if (v != null && v.getNovelId().equals(novelId)) {
                        targetVolumes.add(v);
                    }
                }
            }

            if (targetVolumes == null || targetVolumes.isEmpty()) {
                return ResponseEntity.badRequest().body(new ErrorResponse("当前小说暂无可用卷"));
            }

            // 逐卷提交异步任务，返回taskId与volumeId的映射
            java.util.List<java.util.Map<String, Object>> tasks = new java.util.ArrayList<>();
            for (com.novel.domain.entity.NovelVolume volume : targetVolumes) {
                java.util.Map<String, Object> res = volumeService.generateVolumeOutlineAsync(volume.getId(), userAdvice, aiConfig);
                Object taskId = res.get("taskId");
                if (taskId instanceof Number) {
                    java.util.Map<String, Object> taskInfo = new java.util.HashMap<>();
                    taskInfo.put("taskId", ((Number) taskId).longValue());
                    taskInfo.put("volumeId", volume.getId());
                    taskInfo.put("volumeTitle", volume.getTitle());
                    tasks.add(taskInfo);
                }
            }

            java.util.Map<String, Object> response = new java.util.HashMap<>();
            response.put("novelId", novelId);
            response.put("volumeCount", targetVolumes.size());
            response.put("tasks", tasks);  // 返回详细的任务信息
            response.put("mode", (userAdvice != null && !userAdvice.trim().isEmpty()) ? "WITH_ADVICE" : "THEME_ONLY");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    // DTO类
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
