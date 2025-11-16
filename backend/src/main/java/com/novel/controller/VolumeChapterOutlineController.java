package com.novel.controller;

import com.novel.domain.entity.VolumeChapterOutline;
import com.novel.dto.AIConfigRequest;
import com.novel.repository.VolumeChapterOutlineRepository;
import com.novel.service.VolumeChapterOutlineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/volumes")
public class VolumeChapterOutlineController {

    @Autowired
    private VolumeChapterOutlineService service;

    @Autowired
    private VolumeChapterOutlineRepository outlineRepository;

    /**
     * 为指定卷批量生成章节大纲（细纲）
     * 参数结构参考 /agentic/graph/regenerate-metadata 接口
     * 支持扁平化的 AI 配置参数（provider、apiKey、model、baseUrl）
     */
    @PostMapping("/{volumeId}/chapter-outlines/generate")
    public ResponseEntity<?> generateChapterOutlines(
            @PathVariable("volumeId") Long volumeId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        // 解析参数
        Integer count = null;
        Boolean includeDecisionLog = false;

        if (request != null) {
            if (request.containsKey("count") && request.get("count") != null) {
                count = ((Number) request.get("count")).intValue();
            }
            if (request.containsKey("includeDecisionLog") && request.get("includeDecisionLog") != null) {
                includeDecisionLog = (Boolean) request.get("includeDecisionLog");
            }
        }

        // 提取 AI 配置（扁平化参数）
        AIConfigRequest aiConfig = extractAIConfig(request);

        // 调用服务生成章纲
        Map<String, Object> result = service.generateOutlinesForVolume(volumeId, count, aiConfig);

        // 允许按需剥离 react_decision_log，避免响应过大
        if (!includeDecisionLog) {
            result.remove("react_decision_log");
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 获取小说的所有章纲
     * GET /volumes/chapter-outlines/by-novel/{novelId}
     */
    @GetMapping("/chapter-outlines/by-novel/{novelId}")
    public ResponseEntity<?> getAllChapterOutlinesByNovel(@PathVariable("novelId") Long novelId) {
        try {
            java.util.List<VolumeChapterOutline> outlines = outlineRepository.findByNovelId(novelId);
            return ResponseEntity.ok(outlines);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取章纲列表失败");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 获取指定卷的所有章纲
     * GET /volumes/{volumeId}/chapter-outlines
     * 支持 summary=true 参数，只返回列表所需的基本字段（性能优化）
     */
    @GetMapping("/{volumeId}/chapter-outlines")
    public ResponseEntity<?> getChapterOutlinesByVolume(
            @PathVariable("volumeId") Long volumeId,
            @RequestParam(value = "summary", required = false, defaultValue = "false") boolean summary) {
        try {
            java.util.List<VolumeChapterOutline> outlines = outlineRepository.findByVolumeId(volumeId);

            if (summary) {
                // 只返回列表所需的基本字段，减少数据传输
                java.util.List<Map<String, Object>> summaryList = new java.util.ArrayList<>();
                for (VolumeChapterOutline outline : outlines) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", outline.getId());
                    item.put("globalChapterNumber", outline.getGlobalChapterNumber());
                    item.put("chapterInVolume", outline.getChapterInVolume());
                    item.put("emotionalTone", outline.getEmotionalTone());
                    item.put("status", outline.getStatus());
                    summaryList.add(item);
                }
                return ResponseEntity.ok(summaryList);
            }

            return ResponseEntity.ok(outlines);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取卷章纲列表失败");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 获取单个章节的章纲
     * GET /volumes/chapter-outline?novelId={novelId}&chapterNumber={chapterNumber}
     */
    @GetMapping("/chapter-outline")
    public ResponseEntity<?> getChapterOutline(
            @RequestParam("novelId") Long novelId,
            @RequestParam("chapterNumber") Integer chapterNumber
    ) {
        try {
            VolumeChapterOutline outline = outlineRepository.findByNovelAndGlobalChapter(novelId, chapterNumber);

            if (outline == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("hasOutline", false);
                response.put("message", "当前章节暂无章纲");
                return ResponseEntity.ok(response);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("hasOutline", true);
            response.put("outline", outline);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 更新单个章节的章纲
     * PUT /volumes/chapter-outline/{outlineId}
     */
    @PutMapping("/chapter-outline/{outlineId}")
    public ResponseEntity<?> updateChapterOutline(
            @PathVariable("outlineId") Long outlineId,
            @RequestBody Map<String, Object> request
    ) {
        try {
            VolumeChapterOutline outline = outlineRepository.selectById(outlineId);

            if (outline == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "章纲不存在");
                return ResponseEntity.badRequest().body(error);
            }

            // 更新字段
            if (request.containsKey("direction")) {
                outline.setDirection((String) request.get("direction"));
            }
            if (request.containsKey("keyPlotPoints")) {
                outline.setKeyPlotPoints((String) request.get("keyPlotPoints"));
            }
            if (request.containsKey("emotionalTone")) {
                outline.setEmotionalTone((String) request.get("emotionalTone"));
            }
            if (request.containsKey("foreshadowAction")) {
                outline.setForeshadowAction((String) request.get("foreshadowAction"));
            }
            if (request.containsKey("foreshadowDetail")) {
                outline.setForeshadowDetail((String) request.get("foreshadowDetail"));
            }
            if (request.containsKey("subplot")) {
                outline.setSubplot((String) request.get("subplot"));
            }
            if (request.containsKey("antagonism")) {
                outline.setAntagonism((String) request.get("antagonism"));
            }

            outlineRepository.updateById(outline);

            return ResponseEntity.ok(outline);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 从请求中提取 AI 配置
     * 支持扁平化参数（provider、apiKey、model、baseUrl）
     * 参考 GraphManagementController.extractAIConfig
     */
    private AIConfigRequest extractAIConfig(Map<String, Object> request) {
        if (request == null) {
            return null;
        }

        // 优先从根级别读取扁平化参数
        Object providerObj = request.get("provider");
        Object apiKeyObj = request.get("apiKey");
        Object modelObj = request.get("model");
        Object baseUrlObj = request.get("baseUrl");

        if (providerObj instanceof String || apiKeyObj instanceof String ||
            modelObj instanceof String || baseUrlObj instanceof String) {
            AIConfigRequest config = new AIConfigRequest();
            if (providerObj instanceof String) {
                config.setProvider(((String) providerObj).trim());
            }
            if (apiKeyObj instanceof String) {
                config.setApiKey(((String) apiKeyObj).trim());
            }
            if (modelObj instanceof String) {
                config.setModel(((String) modelObj).trim());
            }
            if (baseUrlObj instanceof String) {
                config.setBaseUrl(((String) baseUrlObj).trim());
            }
            return config;
        }

        // 兼容嵌套的 aiConfig 对象
        @SuppressWarnings("unchecked")
        Map<String, Object> aiConfigMap = request.get("aiConfig") instanceof Map ?
            (Map<String, Object>) request.get("aiConfig") : null;
        if (aiConfigMap != null) {
            AIConfigRequest config = new AIConfigRequest();
            Object provider = aiConfigMap.get("provider");
            Object apiKey = aiConfigMap.get("apiKey");
            Object model = aiConfigMap.get("model");
            Object baseUrl = aiConfigMap.get("baseUrl");
            if (provider instanceof String) {
                config.setProvider(((String) provider).trim());
            }
            if (apiKey instanceof String) {
                config.setApiKey(((String) apiKey).trim());
            }
            if (model instanceof String) {
                config.setModel(((String) model).trim());
            }
            if (baseUrl instanceof String) {
                config.setBaseUrl(((String) baseUrl).trim());
            }
            return config;
        }

        return null;
    }
}

