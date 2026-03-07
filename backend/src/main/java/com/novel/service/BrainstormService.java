package com.novel.service;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.concurrent.*;

/**
 * 分阶段多线路章纲生成服务
 * 
 * 逻辑：
 * 1. 分3个阶段：前10章、中10章、后15章
 * 2. 每阶段并发生成10条线路
 * 3. 用户勾选节点后生成该阶段章纲
 * 4. 基于已生成章纲继续下一阶段
 */
@Service
public class BrainstormService {

    private static final Logger logger = LoggerFactory.getLogger(BrainstormService.class);

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
    
    // 线程池用于并发生成
    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    /**
     * 生成某阶段的10条线路（并发）
     */
    public Map<String, Object> generatePhaseStorylines(
        Long volumeId, 
        Long novelId, 
        int phase,
        int startChapter,
        int endChapter,
        List<Map<String, Object>> previousOutlines,
        AIConfigRequest aiConfig
    ) {
        logger.info("🎯 生成阶段{}线路: 第{}-{}章", phase, startChapter, endChapter);

        NovelVolume volume = volumeMapper.selectById(volumeId);
        if (volume == null) throw new RuntimeException("卷不存在");

        Novel novel = novelRepository.selectById(novelId);
        if (novel == null) throw new RuntimeException("小说不存在");

        NovelOutline outline = outlineRepository.findByNovelIdAndStatus(
            novelId, NovelOutline.OutlineStatus.CONFIRMED
        ).orElse(null);

        if (aiConfig == null) throw new RuntimeException("AI配置不能为空");

        // 并发生成10条线路
        List<Future<Map<String, Object>>> futures = new ArrayList<>();
        
        for (int i = 1; i <= 10; i++) {
            final int lineNum = i;
            futures.add(executor.submit(() -> {
                try {
                    String prompt = buildPhaseStorylinePrompt(
                        novel, outline, volume, lineNum, 
                        startChapter, endChapter, previousOutlines
                    );
                    String aiResponse = aiWritingService.generateContent(
                        prompt, "phase" + phase + "_line" + lineNum, aiConfig
                    );
                    return parseStoryline(aiResponse, lineNum, startChapter);
                } catch (Exception e) {
                    logger.error("线路{}生成失败: {}", lineNum, e.getMessage());
                    return null;
                }
            }));
        }

        // 收集结果
        List<Map<String, Object>> storylines = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                Map<String, Object> result = futures.get(i).get(120, TimeUnit.SECONDS);
                if (result != null) {
                    storylines.add(result);
                    logger.info("✅ 线路{}完成", i + 1);
                }
            } catch (Exception e) {
                logger.error("获取线路{}结果失败: {}", i + 1, e.getMessage());
            }
        }

        logger.info("✅ 阶段{}完成，生成{}条线路", phase, storylines.size());

        Map<String, Object> result = new HashMap<>();
        result.put("storylines", storylines);
        return result;
    }

    /**
     * 根据选中节点生成某阶段的章纲
     */
    public Map<String, Object> generatePhaseOutlines(
        Long volumeId,
        Long novelId,
        int phase,
        int startChapter,
        int endChapter,
        List<Map<String, Object>> selectedNodes,
        List<Map<String, Object>> previousOutlines,
        AIConfigRequest aiConfig
    ) {
        logger.info("📝 生成阶段{}章纲: 第{}-{}章, 选中{}个节点", 
            phase, startChapter, endChapter, selectedNodes.size());

        NovelVolume volume = volumeMapper.selectById(volumeId);
        if (volume == null) throw new RuntimeException("卷不存在");

        Novel novel = novelRepository.selectById(novelId);
        if (novel == null) throw new RuntimeException("小说不存在");

        NovelOutline outline = outlineRepository.findByNovelIdAndStatus(
            novelId, NovelOutline.OutlineStatus.CONFIRMED
        ).orElse(null);

        if (aiConfig == null) throw new RuntimeException("AI配置不能为空");

        String prompt = buildPhaseOutlinePrompt(
            novel, outline, volume, 
            startChapter, endChapter, 
            selectedNodes, previousOutlines
        );

        String aiResponse = aiWritingService.generateContent(prompt, "phase" + phase + "_outline", aiConfig);
        List<Map<String, Object>> outlines = parseOutlines(aiResponse);

        logger.info("✅ 阶段{}章纲生成完成，共{}章", phase, outlines.size());

        Map<String, Object> result = new HashMap<>();
        result.put("outlines", outlines);
        return result;
    }

    /**
     * 兼容旧接口
     */
    public Map<String, Object> brainstormPlots(Long volumeId, Long novelId, AIConfigRequest aiConfig) {
        return generatePhaseStorylines(volumeId, novelId, 1, 1, 10, new ArrayList<>(), aiConfig);
    }

    public Map<String, Object> generateOutlinesFromPlots(
        Long volumeId, Long novelId,
        List<Map<String, Object>> selectedPlots,
        Integer totalChapters,
        AIConfigRequest aiConfig
    ) {
        return generatePhaseOutlines(volumeId, novelId, 1, 1, 10, selectedPlots, new ArrayList<>(), aiConfig);
    }

    /**
     * 保存章纲
     */
    @Transactional
    public void saveChapterOutlines(Long volumeId, List<Map<String, Object>> outlines) {
        logger.info("💾 保存章纲: volumeId={}, count={}", volumeId, outlines.size());

        NovelVolume volume = volumeMapper.selectById(volumeId);
        if (volume == null) throw new RuntimeException("卷不存在");

        chapterOutlineRepository.deleteByVolumeId(volumeId);

        for (Map<String, Object> outlineData : outlines) {
            VolumeChapterOutline chapterOutline = new VolumeChapterOutline();
            chapterOutline.setNovelId(volume.getNovelId());
            chapterOutline.setVolumeId(volumeId);
            
            Object chapterNum = outlineData.get("chapterNumber");
            if (chapterNum instanceof Number) {
                chapterOutline.setChapterInVolume(((Number) chapterNum).intValue());
            }
            
            @SuppressWarnings("unchecked")
            List<String> plotPoints = (List<String>) outlineData.get("plotPoints");
            if (plotPoints != null && !plotPoints.isEmpty()) {
                chapterOutline.setDirection(String.join("\n", plotPoints));
                try {
                    chapterOutline.setKeyPlotPoints(mapper.writeValueAsString(plotPoints));
                } catch (Exception e) {
                    logger.warn("序列化失败", e);
                }
            }
            
            chapterOutline.setStatus("PENDING");
            chapterOutline.setCreatedAt(LocalDateTime.now());
            chapterOutline.setUpdatedAt(LocalDateTime.now());
            chapterOutlineRepository.insert(chapterOutline);
        }

        logger.info("✅ 保存成功");
    }

    // ============ 提示词构建 ============

    private String buildPhaseStorylinePrompt(
        Novel novel, NovelOutline outline, NovelVolume volume,
        int lineNumber, int startChapter, int endChapter,
        List<Map<String, Object>> previousOutlines
    ) {
        StringBuilder sb = new StringBuilder();
        int chapterCount = endChapter - startChapter + 1;
        
        // 铁律
        sb.append("【铁律】\n");
        sb.append("1. 直接输出JSON数组，第一个字符必须是[\n");
        sb.append("2. 禁止```json标记和解释文字\n");
        sb.append("3. 格式：[{\"chapter\":").append(startChapter).append(",\"node\":\"8-15字剧情\"},...]\n");
        sb.append("4. 必须生成").append(chapterCount).append("个节点(第").append(startChapter).append("-").append(endChapter).append("章)\n\n");
        
        // 世界观
        sb.append("【世界观】\n");
        sb.append("书名：").append(novel.getTitle()).append("\n");
        sb.append("类型：").append(novel.getGenre() != null ? novel.getGenre() : "未知").append("\n");
        if (novel.getDescription() != null && !novel.getDescription().isEmpty()) {
            sb.append("简介：").append(novel.getDescription()).append("\n");
        }
        if (outline != null && outline.getPlotStructure() != null) {
            sb.append("大纲：").append(outline.getPlotStructure()).append("\n");
        }
        sb.append("当前卷：").append(volume.getTitle()).append("\n");
        if (volume.getContentOutline() != null) {
            sb.append("卷蓝图：").append(volume.getContentOutline()).append("\n");
        }
        sb.append("\n");
        
        // 前文章纲（如果有）
        if (previousOutlines != null && !previousOutlines.isEmpty()) {
            sb.append("【前文章纲-必须衔接】\n");
            for (Map<String, Object> prev : previousOutlines) {
                Object ch = prev.get("chapterNumber");
                @SuppressWarnings("unchecked")
                List<String> points = (List<String>) prev.get("plotPoints");
                if (points != null && !points.isEmpty()) {
                    sb.append("第").append(ch).append("章：").append(points.get(0)).append("\n");
                }
            }
            sb.append("\n");
        }
        
        // 线路风格
        sb.append("【线路").append(lineNumber).append("风格】\n");
        switch (lineNumber) {
            case 1: sb.append("稳扎稳打型：按部就班推进主线\n"); break;
            case 2: sb.append("爽文爆发型：多打脸、逆袭、装逼\n"); break;
            case 3: sb.append("虐心反转型：先虐后甜、大起大落\n"); break;
            case 4: sb.append("权谋布局型：多阴谋、算计、反转\n"); break;
            case 5: sb.append("情感纠葛型：多感情戏、误会、虐恋\n"); break;
            case 6: sb.append("热血燃向型：多战斗、突破、逆境翻盘\n"); break;
            case 7: sb.append("悬疑揭秘型：多伏笔、谜团、真相揭露\n"); break;
            case 8: sb.append("日常轻松型：多搞笑、互动、温馨\n"); break;
            case 9: sb.append("黑化复仇型：多仇恨、清算、手刃仇人\n"); break;
            case 10: sb.append("奇遇机缘型：多金手指、意外收获\n"); break;
        }
        sb.append("\n");
        
        sb.append("现在生成第").append(startChapter).append("-").append(endChapter)
          .append("章的").append(chapterCount).append("个节点，直接输出JSON：");

        return sb.toString();
    }

    private String buildPhaseOutlinePrompt(
        Novel novel, NovelOutline outline, NovelVolume volume,
        int startChapter, int endChapter,
        List<Map<String, Object>> selectedNodes,
        List<Map<String, Object>> previousOutlines
    ) {
        StringBuilder sb = new StringBuilder();
        int chapterCount = endChapter - startChapter + 1;
        
        // 铁律
        sb.append("【铁律】\n");
        sb.append("1. 直接输出JSON数组，第一个字符必须是[\n");
        sb.append("2. 禁止```json标记和解释文字\n");
        sb.append("3. 格式：[{\"chapterNumber\":").append(startChapter)
          .append(",\"plotPoints\":[\"剧情1\",\"剧情2\",...]},...]");
        sb.append("\n4. 必须生成").append(chapterCount).append("章(第")
          .append(startChapter).append("-").append(endChapter).append("章)\n\n");
        
        // 世界观
        sb.append("【世界观】\n");
        sb.append("书名：").append(novel.getTitle()).append("\n");
        if (outline != null && outline.getPlotStructure() != null) {
            sb.append("大纲：").append(outline.getPlotStructure()).append("\n");
        }
        sb.append("当前卷：").append(volume.getTitle()).append("\n");
        if (volume.getContentOutline() != null) {
            sb.append("卷蓝图：").append(volume.getContentOutline()).append("\n");
        }
        sb.append("\n");
        
        // 前文章纲
        if (previousOutlines != null && !previousOutlines.isEmpty()) {
            sb.append("【前文章纲-必须衔接】\n");
            for (Map<String, Object> prev : previousOutlines) {
                Object ch = prev.get("chapterNumber");
                @SuppressWarnings("unchecked")
                List<String> points = (List<String>) prev.get("plotPoints");
                if (points != null) {
                    sb.append("第").append(ch).append("章：").append(String.join("；", points)).append("\n");
                }
            }
            sb.append("\n");
        }
        
        // 选中节点
        sb.append("【用户选中的节点-必须融入】\n");
        for (int i = 0; i < selectedNodes.size(); i++) {
            Map<String, Object> node = selectedNodes.get(i);
            sb.append(i + 1).append(". [线路").append(node.get("lineNumber"))
              .append("][第").append(node.get("chapter")).append("章位置] ")
              .append(node.get("node")).append("\n");
        }
        sb.append("\n");
        
        // 规则
        sb.append("【规则】\n");
        sb.append("1. 每章4-6条剧情，每条10-30字\n");
        sb.append("2. 所有选中节点必须出现\n");
        sb.append("3. 与前文章纲自然衔接\n");
        sb.append("4. 每章结尾留钩子\n\n");
        
        sb.append("现在生成第").append(startChapter).append("-").append(endChapter)
          .append("章章纲，直接输出JSON：");

        return sb.toString();
    }

    // ============ 解析方法 ============

    private Map<String, Object> parseStoryline(String aiResponse, int lineNumber, int startChapter) {
        try {
            String json = extractJson(aiResponse);
            List<Map<String, Object>> nodes = mapper.readValue(json, new TypeReference<List<Map<String, Object>>>(){});
            
            List<Map<String, Object>> validNodes = new ArrayList<>();
            for (Map<String, Object> node : nodes) {
                if (node.containsKey("node") || node.containsKey("chapter")) {
                    node.put("lineNumber", lineNumber);
                    Object chapter = node.get("chapter");
                    node.put("id", "line" + lineNumber + "_ch" + chapter);
                    validNodes.add(node);
                }
            }
            
            Map<String, Object> storyline = new HashMap<>();
            storyline.put("lineNumber", lineNumber);
            storyline.put("style", getLineStyle(lineNumber));
            storyline.put("nodes", validNodes);
            
            return storyline;
        } catch (Exception e) {
            logger.error("解析线路{}失败: {}", lineNumber, e.getMessage());
            return null;
        }
    }
    
    private String getLineStyle(int lineNumber) {
        String[] styles = {"稳扎稳打", "爽文爆发", "虐心反转", "权谋布局", "情感纠葛",
                          "热血燃向", "悬疑揭秘", "日常轻松", "黑化复仇", "奇遇机缘"};
        return lineNumber > 0 && lineNumber <= 10 ? styles[lineNumber - 1] : "未知";
    }

    private List<Map<String, Object>> parseOutlines(String aiResponse) {
        try {
            String json = extractJson(aiResponse);
            return mapper.readValue(json, new TypeReference<List<Map<String, Object>>>(){});
        } catch (Exception e) {
            logger.error("解析章纲失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String extractJson(String response) {
        if (response == null || response.isEmpty()) return "[]";
        
        if (response.contains("```json")) {
            int start = response.indexOf("```json") + 7;
            int end = response.indexOf("```", start);
            if (end > start) return cleanJson(response.substring(start, end).trim());
        } else if (response.contains("```")) {
            int start = response.indexOf("```") + 3;
            int end = response.indexOf("```", start);
            if (end > start) return cleanJson(response.substring(start, end).trim());
        }

        int arrayStart = response.indexOf('[');
        int arrayEnd = response.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return cleanJson(response.substring(arrayStart, arrayEnd + 1).trim());
        }

        return "[]";
    }
    
    private String cleanJson(String json) {
        if (json == null) return "[]";
        json = json.replaceAll("[\\x00-\\x1F]", "");
        if (json.startsWith("\uFEFF")) json = json.substring(1);
        int start = json.indexOf('[');
        if (start > 0) json = json.substring(start);
        int end = json.lastIndexOf(']');
        if (end > 0 && end < json.length() - 1) json = json.substring(0, end + 1);
        return json.trim();
    }
}
