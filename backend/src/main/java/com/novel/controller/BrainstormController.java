package com.novel.controller;

import com.novel.dto.AIConfigRequest;
import com.novel.service.BrainstormService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 分阶段多线路章纲生成控制器
 */
@RestController
@RequestMapping("/volumes/{volumeId}")
public class BrainstormController {

    private static final Logger logger = LoggerFactory.getLogger(BrainstormController.class);

    @Autowired
    private BrainstormService brainstormService;

    /**
     * 生成某阶段的10条线路（并发）
     * POST /volumes/{volumeId}/generate-phase-storylines
     */
    @PostMapping("/generate-phase-storylines")
    public ResponseEntity<?> generatePhaseStorylines(
            @PathVariable("volumeId") Long volumeId,
            @RequestBody Map<String, Object> request
    ) {
        try {
            Long novelId = extractLongParam(request, "novelId");
            Integer phase = extractIntParam(request, "phase");
            Integer startChapter = extractIntParam(request, "startChapter");
            Integer endChapter = extractIntParam(request, "endChapter");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> previousOutlines = 
                (List<Map<String, Object>>) request.getOrDefault("previousOutlines", new ArrayList<>());
            
            AIConfigRequest aiConfig = extractAIConfig(request);

            Map<String, Object> result = brainstormService.generatePhaseStorylines(
                volumeId, novelId, 
                phase != null ? phase : 1,
                startChapter != null ? startChapter : 1,
                endChapter != null ? endChapter : 10,
                previousOutlines,
                aiConfig
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("生成线路失败: volumeId={}", volumeId, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 根据选中节点生成某阶段章纲
     * POST /volumes/{volumeId}/generate-phase-outlines
     */
    @PostMapping("/generate-phase-outlines")
    public ResponseEntity<?> generatePhaseOutlines(
            @PathVariable("volumeId") Long volumeId,
            @RequestBody Map<String, Object> request
    ) {
        try {
            Long novelId = extractLongParam(request, "novelId");
            Integer phase = extractIntParam(request, "phase");
            Integer startChapter = extractIntParam(request, "startChapter");
            Integer endChapter = extractIntParam(request, "endChapter");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> selectedNodes = 
                (List<Map<String, Object>>) request.getOrDefault("selectedNodes", new ArrayList<>());
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> previousOutlines = 
                (List<Map<String, Object>>) request.getOrDefault("previousOutlines", new ArrayList<>());
            
            AIConfigRequest aiConfig = extractAIConfig(request);

            Map<String, Object> result = brainstormService.generatePhaseOutlines(
                volumeId, novelId,
                phase != null ? phase : 1,
                startChapter != null ? startChapter : 1,
                endChapter != null ? endChapter : 10,
                selectedNodes,
                previousOutlines,
                aiConfig
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("生成章纲失败: volumeId={}", volumeId, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 兼容旧接口：脑洞风暴
     */
    @PostMapping("/brainstorm-plots")
    public ResponseEntity<?> brainstormPlots(
            @PathVariable("volumeId") Long volumeId,
            @RequestBody Map<String, Object> request
    ) {
        try {
            Long novelId = extractLongParam(request, "novelId");
            AIConfigRequest aiConfig = extractAIConfig(request);

            Map<String, Object> result = brainstormService.brainstormPlots(volumeId, novelId, aiConfig);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("脑洞风暴失败: volumeId={}", volumeId, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 兼容旧接口：根据选中剧情点生成章纲
     */
    @PostMapping("/generate-outlines-from-plots")
    public ResponseEntity<?> generateOutlinesFromPlots(
            @PathVariable("volumeId") Long volumeId,
            @RequestBody Map<String, Object> request
    ) {
        try {
            Long novelId = extractLongParam(request, "novelId");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> selectedPlots = 
                (List<Map<String, Object>>) request.get("selectedPlots");
            Integer totalChapters = extractIntParam(request, "totalChapters");
            if (totalChapters == null || totalChapters <= 0) {
                totalChapters = 35;
            }
            AIConfigRequest aiConfig = extractAIConfig(request);

            Map<String, Object> result = brainstormService.generateOutlinesFromPlots(
                volumeId, novelId, selectedPlots, totalChapters, aiConfig
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("生成章纲失败: volumeId={}", volumeId, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 保存章纲
     */
    @PostMapping("/save-chapter-outlines")
    public ResponseEntity<?> saveChapterOutlines(
            @PathVariable("volumeId") Long volumeId,
            @RequestBody Map<String, Object> request
    ) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> outlines = 
                (List<Map<String, Object>>) request.get("outlines");

            brainstormService.saveChapterOutlines(volumeId, outlines);
            return ResponseEntity.ok(Map.of("status", "success", "message", "章纲保存成功"));
        } catch (Exception e) {
            logger.error("保存章纲失败: volumeId={}", volumeId, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ============ 辅助方法 ============

    private AIConfigRequest extractAIConfig(Map<String, Object> request) {
        if (request == null) return null;

        @SuppressWarnings("unchecked")
        Map<String, Object> aiConfigMap = request.get("aiConfig") instanceof Map ?
            (Map<String, Object>) request.get("aiConfig") : null;
        
        if (aiConfigMap != null) {
            AIConfigRequest config = new AIConfigRequest();
            Object provider = aiConfigMap.get("provider");
            Object apiKey = aiConfigMap.get("apiKey");
            Object model = aiConfigMap.get("model");
            Object baseUrl = aiConfigMap.get("baseUrl");
            if (provider instanceof String) config.setProvider(((String) provider).trim());
            if (apiKey instanceof String) config.setApiKey(((String) apiKey).trim());
            if (model instanceof String) config.setModel(((String) model).trim());
            if (baseUrl instanceof String) config.setBaseUrl(((String) baseUrl).trim());
            return config;
        }

        return null;
    }

    private Long extractLongParam(Map<String, Object> request, String key) {
        Object value = request.get(key);
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) return Long.parseLong((String) value);
        return null;
    }

    private Integer extractIntParam(Map<String, Object> request, String key) {
        Object value = request.get(key);
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) return Integer.parseInt((String) value);
        return null;
    }
}
