package com.novel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.domain.entity.*;
import com.novel.dto.AIConfigRequest;
import com.novel.mapper.NovelVolumeMapper;
import com.novel.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能章纲生成服务
 * 核心设计：分批迭代生成 + 看点驱动 + 质量检测
 * 规避AI一次性生成长篇内容时的平庸化问题
 */
@Service
public class SmartChapterOutlineService {

    private static final Logger logger = LoggerFactory.getLogger(SmartChapterOutlineService.class);

    @Autowired
    private NovelVolumeMapper volumeMapper;

    @Autowired
    private NovelRepository novelRepository;

    @Autowired
    private NovelOutlineRepository outlineRepository;

    @Autowired
    private VolumeChapterOutlineRepository chapterOutlineRepository;

    @Autowired
    private AIWritingService aiWritingService;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 第一步：从大纲和卷蓝图中提取看点
     */
    public Map<String, Object> extractHighlights(Long volumeId, Long novelId, AIConfigRequest aiConfig) {
        logger.info("🔍 开始提取看点: volumeId={}, novelId={}", volumeId, novelId);

        NovelVolume volume = volumeMapper.selectById(volumeId);
        if (volume == null) {
            throw new RuntimeException("卷不存在: " + volumeId);
        }

        Novel novel = novelRepository.selectById(novelId);
        if (novel == null) {
            throw new RuntimeException("小说不存在: " + novelId);
        }

        NovelOutline outline = outlineRepository.findByNovelIdAndStatus(
            novelId, NovelOutline.OutlineStatus.CONFIRMED
        ).orElse(null);

        if (outline == null) {
            throw new RuntimeException("缺少已确认的全书大纲");
        }

        // 构建提示词
        String prompt = buildHighlightExtractionPrompt(novel, outline, volume);

        // 调用AI提取看点
        if (aiConfig == null) {
            throw new RuntimeException("AI配置不能为空，请提供AI配置参数");
        }
        String aiResponse = aiWritingService.generateContent(prompt, "highlight_extraction", aiConfig);

        // 解析AI返回的看点列表
        List<Map<String, Object>> highlights = parseHighlights(aiResponse);

        logger.info("✅ 提取到 {} 个看点", highlights.size());

        Map<String, Object> result = new HashMap<>();
        result.put("highlights", highlights);
        return result;
    }

    /**
     * 第二步：根据节奏模板规划情节单元
     */
    public Map<String, Object> planPlotUnits(
        Long volumeId,
        Integer totalChapters,
        String rhythmTemplateId,
        List<Map<String, Object>> highlights,
        AIConfigRequest aiConfig
    ) {
        logger.info("📋 开始规划情节单元: volumeId={}, totalChapters={}, template={}", 
            volumeId, totalChapters, rhythmTemplateId);

        NovelVolume volume = volumeMapper.selectById(volumeId);
        if (volume == null) {
            throw new RuntimeException("卷不存在: " + volumeId);
        }

        // 根据模板生成情节单元
        List<Map<String, Object>> plotUnits = generatePlotUnitsFromTemplate(
            rhythmTemplateId, totalChapters, highlights
        );

        logger.info("✅ 规划了 {} 个情节单元", plotUnits.size());

        Map<String, Object> result = new HashMap<>();
        result.put("plotUnits", plotUnits);
        return result;
    }

    /**
     * 第三步：生成单个情节单元的章纲
     */
    public Map<String, Object> generatePlotUnitOutlines(
        Long volumeId,
        Map<String, Object> plotUnit,
        List<Map<String, Object>> previousOutlines,
        List<Map<String, Object>> highlights,
        AIConfigRequest aiConfig
    ) {
        String unitName = (String) plotUnit.get("name");
        logger.info("📝 开始生成情节单元章纲: volumeId={}, unit={}", volumeId, unitName);

        NovelVolume volume = volumeMapper.selectById(volumeId);
        if (volume == null) {
            throw new RuntimeException("卷不存在: " + volumeId);
        }

        Novel novel = novelRepository.selectById(volume.getNovelId());
        NovelOutline outline = outlineRepository.findByNovelIdAndStatus(
            volume.getNovelId(), NovelOutline.OutlineStatus.CONFIRMED
        ).orElse(null);

        // 构建提示词
        String prompt = buildUnitGenerationPrompt(
            novel, outline, volume, plotUnit, previousOutlines, highlights
        );

        // 调用AI生成章纲
        if (aiConfig == null) {
            throw new RuntimeException("AI配置不能为空，请提供AI配置参数");
        }
        String aiResponse = aiWritingService.generateContent(prompt, "unit_generation", aiConfig);
        
        // 解析章纲列表
        List<Map<String, Object>> outlines = parseChapterOutlines(aiResponse, plotUnit);

        logger.info("✅ 生成了 {} 章章纲", outlines.size());

        Map<String, Object> result = new HashMap<>();
        result.put("outlines", outlines);
        return result;
    }

    /**
     * 第四步：质量检查
     */
    public Map<String, Object> checkOutlineQuality(
        Long volumeId,
        List<Map<String, Object>> outlines,
        AIConfigRequest aiConfig
    ) {
        logger.info("🔍 开始质量检查: volumeId={}, outlineCount={}", volumeId, outlines.size());

        // 构建质量检查提示词
        String prompt = buildQualityCheckPrompt(outlines);

        // 调用AI进行质量评估
        if (aiConfig == null) {
            throw new RuntimeException("AI配置不能为空，请提供AI配置参数");
        }
        String aiResponse = aiWritingService.generateContent(prompt, "quality_check", aiConfig);
        
        // 解析质量报告
        Map<String, Object> report = parseQualityReport(aiResponse);

        logger.info("✅ 质量评分: {}", report.get("overallScore"));

        Map<String, Object> result = new HashMap<>();
        result.put("report", report);
        return result;
    }

    /**
     * 第五步：优化低质量章纲
     */
    public Map<String, Object> optimizeOutlines(
        Long volumeId,
        List<Map<String, Object>> outlines,
        List<Map<String, Object>> issues,
        AIConfigRequest aiConfig
    ) {
        logger.info("🔧 开始优化章纲: volumeId={}, issueCount={}", volumeId, issues.size());

        // 构建优化提示词
        String prompt = buildOptimizationPrompt(outlines, issues);

        // 调用AI优化章纲
        if (aiConfig == null) {
            throw new RuntimeException("AI配置不能为空，请提供AI配置参数");
        }
        String aiResponse = aiWritingService.generateContent(prompt, "outline_optimization", aiConfig);
        
        // 解析优化后的章纲
        List<Map<String, Object>> optimizedOutlines = parseChapterOutlines(aiResponse, null);

        logger.info("✅ 优化完成");

        Map<String, Object> result = new HashMap<>();
        result.put("outlines", optimizedOutlines);
        return result;
    }

    /**
     * 保存生成的章纲到数据库
     */
    @Transactional
    public void saveGeneratedOutlines(Long volumeId, List<Map<String, Object>> outlines) {
        logger.info("💾 开始保存章纲: volumeId={}, count={}", volumeId, outlines.size());

        NovelVolume volume = volumeMapper.selectById(volumeId);
        if (volume == null) {
            throw new RuntimeException("卷不存在: " + volumeId);
        }

        // 删除该卷的旧章纲
        chapterOutlineRepository.deleteByVolumeId(volumeId);

        // 保存新章纲
        for (Map<String, Object> outlineData : outlines) {
            VolumeChapterOutline outline = new VolumeChapterOutline();
            outline.setNovelId(volume.getNovelId());
            outline.setVolumeId(volumeId);
            outline.setChapterInVolume(getInt(outlineData, "chapterInVolume"));
            outline.setGlobalChapterNumber(getInt(outlineData, "globalChapterNumber"));
            outline.setDirection(getString(outlineData, "direction"));
            outline.setEmotionalTone(getString(outlineData, "emotionalTone"));
            outline.setForeshadowAction(getString(outlineData, "foreshadowAction"));
            outline.setSubplot(getString(outlineData, "subplot"));
            outline.setStatus("PENDING");
            outline.setCreatedAt(LocalDateTime.now());
            outline.setUpdatedAt(LocalDateTime.now());

            // 处理JSON字段
            try {
                if (outlineData.containsKey("keyPlotPoints")) {
                    outline.setKeyPlotPoints(mapper.writeValueAsString(outlineData.get("keyPlotPoints")));
                }
                if (outlineData.containsKey("foreshadowDetail")) {
                    outline.setForeshadowDetail(mapper.writeValueAsString(outlineData.get("foreshadowDetail")));
                }
                if (outlineData.containsKey("antagonism")) {
                    outline.setAntagonism(mapper.writeValueAsString(outlineData.get("antagonism")));
                }
            } catch (Exception e) {
                logger.warn("JSON序列化失败", e);
            }

            chapterOutlineRepository.insert(outline);
        }

        logger.info("✅ 章纲保存成功");
    }

    // ============ 提示词构建方法 ============

    private String buildHighlightExtractionPrompt(Novel novel, NovelOutline outline, NovelVolume volume) {
        return String.format("""
            # 任务：从小说大纲和卷蓝图中提取核心看点
            
            ## 小说信息
            - 书名：%s
            - 类型：%s
            
            ## 全书大纲
            %s
            
            ## 当前卷信息
            - 卷名：%s
            - 卷蓝图：%s
            
            ## 要求
            请从大纲和卷蓝图中提取出**必须包含的核心看点**，每个看点包括：
            1. type: 看点类型（CONFLICT冲突/REVERSAL反转/CLIMAX高潮/SUSPENSE悬念/PAYOFF伏笔回收/EMOTION情感爆发/FACE_SLAP打脸/POWER_UP升级突破）
            2. description: 看点描述（20字内）
            3. priority: 优先级（HIGH/MEDIUM/LOW）
            4. targetChapter: 建议出现的章节位置（可选）
            
            ## 输出格式（JSON数组）
            ```json
            [
              {
                "id": "h1",
                "type": "CONFLICT",
                "description": "主角与反派首次正面交锋",
                "priority": "HIGH",
                "targetChapter": 5,
                "source": "OUTLINE"
              }
            ]
            ```
            
            请提取5-10个核心看点，确保涵盖本卷的关键剧情节点。
            """,
            novel.getTitle(),
            novel.getGenre(),
            outline.getPlotStructure(),
            volume.getTitle(),
            volume.getContentOutline() != null ? volume.getContentOutline() : "无"
        );
    }

    private String buildUnitGenerationPrompt(
        Novel novel,
        NovelOutline outline,
        NovelVolume volume,
        Map<String, Object> plotUnit,
        List<Map<String, Object>> previousOutlines,
        List<Map<String, Object>> highlights
    ) {
        int startChapter = getInt(plotUnit, "startChapter");
        int endChapter = getInt(plotUnit, "endChapter");
        int chapterCount = endChapter - startChapter + 1;

        StringBuilder previousContext = new StringBuilder();
        if (previousOutlines != null && !previousOutlines.isEmpty()) {
            previousContext.append("## 前文章纲（最近3章）\n");
            int start = Math.max(0, previousOutlines.size() - 3);
            for (int i = start; i < previousOutlines.size(); i++) {
                Map<String, Object> prev = previousOutlines.get(i);
                previousContext.append(String.format("第%d章：%s\n", 
                    getInt(prev, "chapterInVolume"), getString(prev, "direction")));
            }
        }

        StringBuilder highlightContext = new StringBuilder("## 本单元必须包含的看点\n");
        for (Map<String, Object> h : highlights) {
            highlightContext.append(String.format("- [%s] %s\n", 
                getString(h, "type"), getString(h, "description")));
        }

        return String.format("""
            # 任务：生成情节单元的章纲
            
            ## 小说信息
            - 书名：%s
            - 类型：%s
            - 卷名：%s
            
            ## 情节单元信息
            - 单元名：%s
            - 章节范围：第%d-%d章（共%d章）
            - 主题：%s
            - 强度：%s
            
            %s
            
            %s
            
            ## 生成要求
            1. **避免平庸**：每章必须有明确的冲突/悬念/反转，禁止"过渡章"
            2. **看点驱动**：确保上述看点合理分布在各章中
            3. **节奏把控**：根据单元强度调整剧情密度
            4. **钩子结尾**：每章结尾留悬念，吸引读者继续阅读
            5. **连贯性**：与前文自然衔接，不突兀
            
            ## 输出格式（JSON数组）
            ```json
            [
              {
                "chapterInVolume": %d,
                "direction": "章节主线剧情（50字内）",
                "keyPlotPoints": ["关键情节点1", "关键情节点2"],
                "emotionalTone": "紧张/激动/悲伤/温馨",
                "foreshadowAction": "埋下的伏笔",
                "hookEnding": "章末钩子（吸引读者的悬念）"
              }
            ]
            ```
            
            请生成%d章章纲，确保质量高于AI平均水平。
            """,
            novel.getTitle(),
            novel.getGenre(),
            volume.getTitle(),
            getString(plotUnit, "name"),
            startChapter, endChapter, chapterCount,
            getString(plotUnit, "theme"),
            getString(plotUnit, "intensity"),
            previousContext.toString(),
            highlightContext.toString(),
            startChapter,
            chapterCount
        );
    }

    private String buildQualityCheckPrompt(List<Map<String, Object>> outlines) {
        StringBuilder outlinesText = new StringBuilder();
        for (Map<String, Object> outline : outlines) {
            outlinesText.append(String.format("第%d章：%s\n", 
                getInt(outline, "chapterInVolume"), getString(outline, "direction")));
        }

        return String.format("""
            # 任务：章纲质量检查
            
            ## 待检查的章纲
            %s
            
            ## 检查维度
            1. **平庸度检测**：是否存在"过渡章"、"日常章"等无冲突内容
            2. **钩子强度**：每章结尾是否有足够吸引力
            3. **冲突密度**：是否每章都有明确的矛盾或悬念
            4. **可预测性**：剧情是否过于套路化
            5. **信息堆砌**：是否存在纯粹的背景介绍章节
            
            ## 输出格式（JSON）
            ```json
            {
              "overallScore": 75,
              "issues": [
                {
                  "type": "BORING_TRANSITION",
                  "chapterIndex": 2,
                  "description": "第3章缺乏冲突，纯粹过渡",
                  "suggestion": "增加意外事件或人物冲突"
                }
              ],
              "strengths": ["节奏紧凑", "悬念设置到位"]
            }
            ```
            
            请给出客观评分（0-100）和具体改进建议。
            """,
            outlinesText.toString()
        );
    }

    private String buildOptimizationPrompt(
        List<Map<String, Object>> outlines,
        List<Map<String, Object>> issues
    ) {
        StringBuilder outlinesText = new StringBuilder();
        for (Map<String, Object> outline : outlines) {
            outlinesText.append(String.format("第%d章：%s\n", 
                getInt(outline, "chapterInVolume"), getString(outline, "direction")));
        }

        StringBuilder issuesText = new StringBuilder();
        for (Map<String, Object> issue : issues) {
            issuesText.append(String.format("- 第%d章：%s（建议：%s）\n",
                getInt(issue, "chapterIndex") + 1,
                getString(issue, "description"),
                getString(issue, "suggestion")
            ));
        }

        return String.format("""
            # 任务：优化低质量章纲
            
            ## 原章纲
            %s
            
            ## 质量问题
            %s
            
            ## 优化要求
            1. 针对每个问题进行针对性改进
            2. 增加冲突强度和悬念设置
            3. 避免平庸化和套路化
            4. 保持与其他章节的连贯性
            
            ## 输出格式（JSON数组）
            请输出优化后的完整章纲列表（与原格式相同）。
            """,
            outlinesText.toString(),
            issuesText.toString()
        );
    }

    // ============ 解析方法 ============

    private List<Map<String, Object>> parseHighlights(String aiResponse) {
        try {
            String json = extractJsonFromResponse(aiResponse);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> highlights = mapper.readValue(json, List.class);
            
            // 确保每个看点都有ID
            for (int i = 0; i < highlights.size(); i++) {
                if (!highlights.get(i).containsKey("id")) {
                    highlights.get(i).put("id", "h" + (i + 1));
                }
                if (!highlights.get(i).containsKey("source")) {
                    highlights.get(i).put("source", "AI_GENERATED");
                }
            }
            
            return highlights;
        } catch (Exception e) {
            logger.error("解析看点失败", e);
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> parseChapterOutlines(String aiResponse, Map<String, Object> plotUnit) {
        try {
            String json = extractJsonFromResponse(aiResponse);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> outlines = mapper.readValue(json, List.class);
            return outlines;
        } catch (Exception e) {
            logger.error("解析章纲失败", e);
            return new ArrayList<>();
        }
    }

    private Map<String, Object> parseQualityReport(String aiResponse) {
        try {
            String json = extractJsonFromResponse(aiResponse);
            @SuppressWarnings("unchecked")
            Map<String, Object> report = mapper.readValue(json, Map.class);
            
            // 确保必要字段存在
            if (!report.containsKey("overallScore")) {
                report.put("overallScore", 60);
            }
            if (!report.containsKey("issues")) {
                report.put("issues", new ArrayList<>());
            }
            if (!report.containsKey("strengths")) {
                report.put("strengths", new ArrayList<>());
            }
            
            return report;
        } catch (Exception e) {
            logger.error("解析质量报告失败", e);
            Map<String, Object> defaultReport = new HashMap<>();
            defaultReport.put("overallScore", 60);
            defaultReport.put("issues", new ArrayList<>());
            defaultReport.put("strengths", new ArrayList<>());
            return defaultReport;
        }
    }

    private String extractJsonFromResponse(String response) {
        // 提取JSON内容（去除markdown代码块标记）
        if (response.contains("```json")) {
            int start = response.indexOf("```json") + 7;
            int end = response.indexOf("```", start);
            if (end > start) {
                return response.substring(start, end).trim();
            }
        } else if (response.contains("```")) {
            int start = response.indexOf("```") + 3;
            int end = response.indexOf("```", start);
            if (end > start) {
                return response.substring(start, end).trim();
            }
        }
        
        // 尝试直接查找JSON数组或对象
        int arrayStart = response.indexOf('[');
        int objectStart = response.indexOf('{');
        
        if (arrayStart >= 0 && (objectStart < 0 || arrayStart < objectStart)) {
            int arrayEnd = response.lastIndexOf(']');
            if (arrayEnd > arrayStart) {
                return response.substring(arrayStart, arrayEnd + 1).trim();
            }
        } else if (objectStart >= 0) {
            int objectEnd = response.lastIndexOf('}');
            if (objectEnd > objectStart) {
                return response.substring(objectStart, objectEnd + 1).trim();
            }
        }
        
        return response.trim();
    }

    private List<Map<String, Object>> generatePlotUnitsFromTemplate(
        String templateId,
        int totalChapters,
        List<Map<String, Object>> highlights
    ) {
        // 预设的节奏模板
        Map<String, List<Map<String, Object>>> templates = new HashMap<>();
        
        // 波浪式节奏
        templates.put("wave", Arrays.asList(
            createUnit("开局破冰", 1, 5, "MEDIUM", "建立冲突，引入悬念", Arrays.asList("CONFLICT", "SUSPENSE")),
            createUnit("矛盾升级", 6, 11, "HIGH", "冲突加剧，出现反转", Arrays.asList("CONFLICT", "REVERSAL")),
            createUnit("短暂喘息", 12, 15, "LOW", "情感铺垫，伏笔回收", Arrays.asList("EMOTION", "PAYOFF")),
            createUnit("危机爆发", 16, 22, "CLIMAX", "高潮对决，打脸爽点", Arrays.asList("CLIMAX", "FACE_SLAP")),
            createUnit("收尾铺垫", 23, 27, "MEDIUM", "悬念延续，为下卷铺垫", Arrays.asList("SUSPENSE", "PAYOFF"))
        ));
        
        // 递进式节奏
        templates.put("escalation", Arrays.asList(
            createUnit("蛰伏期", 1, 4, "LOW", "积蓄力量，埋下悬念", Arrays.asList("SUSPENSE")),
            createUnit("初露锋芒", 5, 9, "MEDIUM", "小试牛刀，初步打脸", Arrays.asList("CONFLICT", "FACE_SLAP")),
            createUnit("强敌出现", 10, 15, "HIGH", "遭遇强敌，剧情反转", Arrays.asList("CONFLICT", "REVERSAL")),
            createUnit("绝地反击", 16, 22, "HIGH", "高潮对决，实力突破", Arrays.asList("CLIMAX", "POWER_UP")),
            createUnit("碾压收割", 23, 27, "CLIMAX", "全面碾压，爽点密集", Arrays.asList("FACE_SLAP", "PAYOFF"))
        ));
        
        // 悬疑式节奏
        templates.put("suspense", Arrays.asList(
            createUnit("迷雾初现", 1, 5, "MEDIUM", "谜团出现，线索初现", Arrays.asList("SUSPENSE")),
            createUnit("线索交织", 6, 11, "MEDIUM", "多条线索，冲突交织", Arrays.asList("SUSPENSE", "CONFLICT")),
            createUnit("假象破灭", 12, 16, "HIGH", "真相反转，假象破灭", Arrays.asList("REVERSAL")),
            createUnit("真相浮现", 17, 22, "HIGH", "伏笔回收，情感爆发", Arrays.asList("PAYOFF", "EMOTION")),
            createUnit("终极对决", 23, 27, "CLIMAX", "最终对决，真相大白", Arrays.asList("CLIMAX", "FACE_SLAP"))
        ));
        
        // 三幕式经典
        templates.put("three_act", Arrays.asList(
            createUnit("第一幕：建置", 1, 8, "MEDIUM", "世界观建立，冲突引入", Arrays.asList("CONFLICT", "SUSPENSE")),
            createUnit("第二幕A：对抗", 9, 18, "HIGH", "持续对抗，情感深化", Arrays.asList("CONFLICT", "REVERSAL", "EMOTION")),
            createUnit("第二幕B：危机", 19, 25, "CLIMAX", "危机爆发，高潮对决", Arrays.asList("CLIMAX", "REVERSAL")),
            createUnit("第三幕：解决", 26, 30, "HIGH", "问题解决，伏笔回收", Arrays.asList("PAYOFF", "FACE_SLAP"))
        ));
        
        List<Map<String, Object>> template = templates.getOrDefault(templateId, templates.get("wave"));
        
        // 根据实际章节数调整单元范围
        List<Map<String, Object>> adjustedUnits = new ArrayList<>();
        double scale = (double) totalChapters / 27.0; // 默认模板是27章
        
        for (Map<String, Object> unit : template) {
            Map<String, Object> adjustedUnit = new HashMap<>(unit);
            int start = (int) Math.ceil(getInt(unit, "startChapter") * scale);
            int end = (int) Math.ceil(getInt(unit, "endChapter") * scale);
            adjustedUnit.put("startChapter", Math.max(1, start));
            adjustedUnit.put("endChapter", Math.min(totalChapters, end));
            
            // 分配看点到单元
            @SuppressWarnings("unchecked")
            List<String> requiredElements = (List<String>) unit.get("requiredElements");
            List<String> assignedHighlights = new ArrayList<>();
            for (Map<String, Object> h : highlights) {
                if (requiredElements.contains(getString(h, "type"))) {
                    assignedHighlights.add(getString(h, "id"));
                }
            }
            adjustedUnit.put("requiredHighlights", assignedHighlights);
            adjustedUnit.put("status", "PENDING");
            
            adjustedUnits.add(adjustedUnit);
        }
        
        return adjustedUnits;
    }

    private Map<String, Object> createUnit(
        String name, int start, int end, String intensity, String theme, List<String> elements
    ) {
        Map<String, Object> unit = new HashMap<>();
        unit.put("id", "unit_" + start);
        unit.put("name", name);
        unit.put("startChapter", start);
        unit.put("endChapter", end);
        unit.put("intensity", intensity);
        unit.put("theme", theme);
        unit.put("requiredElements", elements);
        return unit;
    }

    // ============ 辅助方法 ============

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : "";
    }

    private int getInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}
