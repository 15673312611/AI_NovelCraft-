package com.novel.controller;

import com.novel.domain.entity.VolumeChapterOutline;
import com.novel.dto.AIConfigRequest;
import com.novel.repository.VolumeChapterOutlineRepository;
import com.novel.service.VolumeChapterOutlineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/volumes")
public class VolumeChapterOutlineController {

    private static final Logger logger = LoggerFactory.getLogger(VolumeChapterOutlineController.class);

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

        if (request != null) {
            if (request.containsKey("count") && request.get("count") != null) {
                count = ((Number) request.get("count")).intValue();
            }
        }

        // 提取 AI 配置（扁平化参数）
        AIConfigRequest aiConfig = extractAIConfig(request);

        // 调用服务生成章纲
        Map<String, Object> result = service.generateOutlinesForVolume(volumeId, count, aiConfig);

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
     * 为指定卷增量生成章节大纲：只为【尚未写正文】的章节生成或补充章纲
     * - 保留已写正文章节对应的章纲，不会清空整卷旧数据
     * - 自动判断当前写到本卷第几章，按需生成后续若干章纲
     * - 支持额外的作者需求/偏好（userRequirements），仅影响后续未写正文的部分
     *
     * POST /volumes/{volumeId}/chapter-outlines/generate-remaining
     * 请求体示例：
     * {
     *   "count": 10,                    // 可选，生成后续多少章，不传则自动等于剩余未写正文章数
     *   "userRequirements": "……",     // 可选，作者本次对后续剧情的特别需求（爽点、情绪走向等）
     *   "provider": "deepseek",       // 以下为扁平化 AI 配置，可选
     *   "apiKey": "xxx",
     *   "model": "deepseek-chat",
     *   "baseUrl": "https://api.xxx.com"
     * }
     */
    @PostMapping("/{volumeId}/chapter-outlines/generate-remaining")
    public ResponseEntity<?> generateRemainingChapterOutlines(
            @PathVariable("volumeId") Long volumeId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        // 解析参数
        Integer count = null;
        String userRequirements = null;

        if (request != null) {
            if (request.containsKey("count") && request.get("count") != null) {
                count = ((Number) request.get("count")).intValue();
            }
            if (request.containsKey("userRequirements") && request.get("userRequirements") != null) {
                Object ur = request.get("userRequirements");
                if (ur instanceof String) {
                    userRequirements = ((String) ur).trim();
                }
            }
        }

        // 提取 AI 配置（扁平化参数）
        AIConfigRequest aiConfig = extractAIConfig(request);

        // 调用服务生成章纲（仅未写正文部分）
        Map<String, Object> result = service.generateOutlinesForRemainingChapters(volumeId, count, aiConfig, userRequirements);

        return ResponseEntity.ok(result);
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
                    // 添加 direction 摘要用于列表预览（截取前60字符）
                    String direction = outline.getDirection();
                    String keyPlotPoints = outline.getKeyPlotPoints();
                    String preview = (keyPlotPoints != null && !keyPlotPoints.isBlank()) ? keyPlotPoints : direction;
                    if (preview != null && preview.length() > 60) {
                        preview = preview.substring(0, 60) + "...";
                    }
                    item.put("direction", preview);
                    item.put("keyPlotPoints", preview);
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
                return ResponseEntity.badRequest().body(Map.of("error", "章纲不存在"));
            }

            // 更新普通字段
            updateFieldIfPresent(request, "direction", outline::setDirection);
            updateFieldIfPresent(request, "emotionalTone", outline::setEmotionalTone);
            updateFieldIfPresent(request, "foreshadowAction", outline::setForeshadowAction);
            updateFieldIfPresent(request, "subplot", outline::setSubplot);
            
            // 更新 JSON 字段（空值时设为 null）
            updateJsonFieldIfPresent(request, "keyPlotPoints", outline::setKeyPlotPoints);
            updateJsonFieldIfPresent(request, "foreshadowDetail", outline::setForeshadowDetail);
            updateJsonFieldIfPresent(request, "antagonism", outline::setAntagonism);

            outlineRepository.updateById(outline);
            return ResponseEntity.ok(outline);
        } catch (Exception e) {
            logger.error("更新章纲失败: outlineId={}", outlineId, e);
            return ResponseEntity.badRequest().body(Map.of("error", "更新章纲失败: " + e.getMessage()));
        }
    }

    /**
     * 创建单个章节的章纲
     * POST /volumes/chapter-outline
     */
    @PostMapping("/chapter-outline")
    public ResponseEntity<?> createChapterOutline(@RequestBody Map<String, Object> request) {
        try {
            // 必填字段校验
            Long novelId = getLong(request, "novelId");
            Long volumeId = getLong(request, "volumeId");
            Integer globalChapterNumber = getInt(request, "globalChapterNumber");
            
            if (novelId == null || volumeId == null || globalChapterNumber == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "缺少必填字段: novelId, volumeId, globalChapterNumber"));
            }
            
            // 检查是否已存在
            VolumeChapterOutline existing = outlineRepository.findByNovelAndGlobalChapter(novelId, globalChapterNumber);
            if (existing != null) {
                return ResponseEntity.badRequest().body(Map.of("error", "该章节已存在章纲，请使用更新接口"));
            }
            
            VolumeChapterOutline outline = new VolumeChapterOutline();
            outline.setNovelId(novelId);
            outline.setVolumeId(volumeId);
            outline.setGlobalChapterNumber(globalChapterNumber);
            
            // 可选字段
            Integer chapterInVolume = getInt(request, "chapterInVolume");
            if (chapterInVolume != null) {
                outline.setChapterInVolume(chapterInVolume);
            }
            
            Integer volumeNumber = getInt(request, "volumeNumber");
            if (volumeNumber != null) {
                outline.setVolumeNumber(volumeNumber);
            }
            
            // 内容字段
            outline.setDirection(getString(request, "direction"));
            String keyPlotPoints = getString(request, "keyPlotPoints");
            outline.setKeyPlotPoints(isBlank(keyPlotPoints) ? null : keyPlotPoints);
            outline.setForeshadowAction(getString(request, "foreshadowAction"));
            // foreshadowDetail 是 JSON 字段，空字符串需要设为 null
            String foreshadowDetail = getString(request, "foreshadowDetail");
            outline.setForeshadowDetail(isBlank(foreshadowDetail) ? null : foreshadowDetail);
            outline.setStatus("PENDING");
            
            outlineRepository.insert(outline);
            logger.info("✅ 创建章纲成功: novelId={}, globalChapterNumber={}, outlineId={}", 
                novelId, globalChapterNumber, outline.getId());
            
            return ResponseEntity.ok(outline);
        } catch (Exception e) {
            logger.error("创建章纲失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", "创建章纲失败: " + e.getMessage()));
        }
    }

    /**
     * 删除指定卷的所有章纲
     * DELETE /volumes/{volumeId}/chapter-outlines
     */
    @DeleteMapping("/{volumeId}/chapter-outlines")
    public ResponseEntity<?> deleteChapterOutlinesByVolume(@PathVariable("volumeId") Long volumeId) {
        try {
            int deletedCount = outlineRepository.deleteByVolumeId(volumeId);
            logger.info("✅ 已删除卷 {} 的所有章纲，共 {} 条", volumeId, deletedCount);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "章纲删除成功");
            response.put("volumeId", volumeId);
            response.put("deletedCount", deletedCount);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("删除章纲失败: volumeId={}", volumeId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "删除章纲失败: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 辅助方法：如果请求中包含指定字段，则更新（普通字段）
     */
    private void updateFieldIfPresent(Map<String, Object> request, String fieldName, java.util.function.Consumer<String> setter) {
        if (request.containsKey(fieldName)) {
            Object value = request.get(fieldName);
            setter.accept(value != null ? String.valueOf(value) : null);
        }
    }

    /**
     * 辅助方法：如果请求中包含指定字段，则更新（JSON 字段）
     * JSON 字段空值时必须设为 null，不能是空字符串，否则 MySQL 会报错
     */
    private void updateJsonFieldIfPresent(Map<String, Object> request, String fieldName, java.util.function.Consumer<String> setter) {
        if (request.containsKey(fieldName)) {
            Object value = request.get(fieldName);
            if (value == null || value.toString().trim().isEmpty()) {
                setter.accept(null);
            } else {
                setter.accept(String.valueOf(value));
            }
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

    /**
     * 辅助方法：从 Map 中获取字符串
     */
    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        return v.toString();
    }

    /**
     * 辅助方法：从 Map 中获取整数
     */
    private Integer getInt(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }

    /**
     * 辅助方法：从 Map 中获取长整数
     */
    private Long getLong(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }

    /**
     * 辅助方法：判断字符串是否为空
     */
    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}

