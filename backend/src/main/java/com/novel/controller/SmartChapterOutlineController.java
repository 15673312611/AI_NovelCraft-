package com.novel.controller;

import com.novel.dto.AIConfigRequest;
import com.novel.service.SmartChapterOutlineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 智能章纲生成控制器
 * 基于看点驱动的分批迭代生成，规避AI平庸化问题
 */
@RestController
@RequestMapping("/volumes/{volumeId}/smart-outline")
public class SmartChapterOutlineController {

    private static final Logger logger = LoggerFactory.getLogger(SmartChapterOutlineController.class);

    @Autowired
    private SmartChapterOutlineService smartOutlineService;

    /**
     * 第一步：从大纲和卷蓝图中提取看点
     * POST /volumes/{volumeId}/smart-outline/extract-highlights
     */
    @PostMapping("/extract-highlights")
    public ResponseEntity<?> extractHighlights(
            @PathVariable("volumeId") Long volumeId,
            @RequestBody Map<String, Object> request
    ) {
        try {
            Long novelId = extractLongParam(request, "novelId");
            AIConfigRequest aiConfig = extractAIConfig(request);
            
            Map<String, Object> result = smartOutlineService.extractHighlights(volumeId, novelId, aiConfig);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("提取看点失败: volumeId={}", volumeId, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 第二步：根据节奏模板规划情节单元
     * POST /volumes/{volumeId}/smart-outline/plan-units
     */
    @PostMapping("/plan-units")
    public ResponseEntity<?> planPlotUnits(
            @PathVariable("volumeId") Long volumeId,
            @RequestBody Map<String, Object> request
    ) {
        try {
            Integer totalChapters = extractIntParam(request, "totalChapters");
            String rhythmTemplateId = (String) request.get("rhythmTemplateId");
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> highlights = 
                (java.util.List<Map<String, Object>>) request.get("highlights");
            AIConfigRequest aiConfig = extractAIConfig(request);
            
            Map<String, Object> result = smartOutlineService.planPlotUnits(
                volumeId, totalChapters, rhythmTemplateId, highlights, aiConfig
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("规划情节单元失败: volumeId={}", volumeId, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 第三步：生成单个情节单元的章纲
     * POST /volumes/{volumeId}/smart-outline/generate-unit
     */
    @PostMapping("/generate-unit")
    public ResponseEntity<?> generatePlotUnitOutlines(
            @PathVariable("volumeId") Long volumeId,
            @RequestBody Map<String, Object> request
    ) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> plotUnit = (Map<String, Object>) request.get("plotUnit");
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> previousOutlines = 
                (java.util.List<Map<String, Object>>) request.get("previousOutlines");
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> highlights = 
                (java.util.List<Map<String, Object>>) request.get("highlights");
            AIConfigRequest aiConfig = extractAIConfig(request);
            
            Map<String, Object> result = smartOutlineService.generatePlotUnitOutlines(
                volumeId, plotUnit, previousOutlines, highlights, aiConfig
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("生成情节单元章纲失败: volumeId={}", volumeId, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 第四步：质量检查
     * POST /volumes/{volumeId}/smart-outline/quality-check
     */
    @PostMapping("/quality-check")
    public ResponseEntity<?> checkOutlineQuality(
            @PathVariable("volumeId") Long volumeId,
            @RequestBody Map<String, Object> request
    ) {
        try {
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> outlines = 
                (java.util.List<Map<String, Object>>) request.get("outlines");
            AIConfigRequest aiConfig = extractAIConfig(request);
            
            Map<String, Object> result = smartOutlineService.checkOutlineQuality(
                volumeId, outlines, aiConfig
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("质量检查失败: volumeId={}", volumeId, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 第五步：优化低质量章纲
     * POST /volumes/{volumeId}/smart-outline/optimize
     */
    @PostMapping("/optimize")
    public ResponseEntity<?> optimizeOutlines(
            @PathVariable("volumeId") Long volumeId,
            @RequestBody Map<String, Object> request
    ) {
        try {
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> outlines = 
                (java.util.List<Map<String, Object>>) request.get("outlines");
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> issues = 
                (java.util.List<Map<String, Object>>) request.get("issues");
            AIConfigRequest aiConfig = extractAIConfig(request);
            
            Map<String, Object> result = smartOutlineService.optimizeOutlines(
                volumeId, outlines, issues, aiConfig
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("优化章纲失败: volumeId={}", volumeId, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 保存生成的章纲到数据库
     * POST /volumes/{volumeId}/smart-outline/save
     */
    @PostMapping("/save")
    public ResponseEntity<?> saveGeneratedOutlines(
            @PathVariable("volumeId") Long volumeId,
            @RequestBody Map<String, Object> request
    ) {
        try {
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> outlines = 
                (java.util.List<Map<String, Object>>) request.get("outlines");
            AIConfigRequest aiConfig = extractAIConfig(request);
            
            smartOutlineService.saveGeneratedOutlines(volumeId, outlines);
            return ResponseEntity.ok(Map.of("status", "success", "message", "章纲保存成功"));
        } catch (Exception e) {
            logger.error("保存章纲失败: volumeId={}", volumeId, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ============ 辅助方法 ============

    private AIConfigRequest extractAIConfig(Map<String, Object> request) {
        if (request == null) return null;

        Object providerObj = request.get("provider");
        Object apiKeyObj = request.get("apiKey");
        Object modelObj = request.get("model");
        Object baseUrlObj = request.get("baseUrl");

        if (providerObj instanceof String || apiKeyObj instanceof String ||
            modelObj instanceof String || baseUrlObj instanceof String) {
            AIConfigRequest config = new AIConfigRequest();
            if (providerObj instanceof String) config.setProvider(((String) providerObj).trim());
            if (apiKeyObj instanceof String) config.setApiKey(((String) apiKeyObj).trim());
            if (modelObj instanceof String) config.setModel(((String) modelObj).trim());
            if (baseUrlObj instanceof String) config.setBaseUrl(((String) baseUrlObj).trim());
            return config;
        }

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
