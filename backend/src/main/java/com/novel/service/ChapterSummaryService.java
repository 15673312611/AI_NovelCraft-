package com.novel.service;

import com.novel.domain.entity.Chapter;
import com.novel.domain.entity.ChapterSummary;
import com.novel.repository.ChapterSummaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 章节概括服务
 * 负责生成、保存和管理章节的简短概括，用于保持长篇小说的连贯性
 * 
 * @author Novel Creation System
 * @version 1.0.0
 */
@Service
public class ChapterSummaryService {

    private static final Logger logger = LoggerFactory.getLogger(ChapterSummaryService.class);

    @Autowired
    private ChapterSummaryRepository chapterSummaryRepository;
    
    @Autowired
    private AIWritingService aiWritingService;
    
    @Autowired
    private com.novel.repository.ChapterRepository chapterRepository;

    /**
     * 生成章节概括（使用后端配置 - 已弃用，建议使用带AIConfigRequest参数的方法）
     * 将章节内容压缩为100-200字的简短概括
     * @deprecated 建议使用 {@link #generateChapterSummary(Chapter, com.novel.dto.AIConfigRequest)}
     */
    @Deprecated
    public String generateChapterSummary(Chapter chapter) {
        logger.info("📝 开始生成章节概括（使用后端配置）: 章节ID={}, 章节号={}", chapter.getId(), chapter.getChapterNumber());
        
        try {
            String content = chapter.getContent();
            if (content == null || content.trim().isEmpty()) {
                return "本章暂无内容";
            }
            
            // 构建概括提示词
            String prompt = buildSummaryPrompt(chapter);
            
            // 调用AI生成概括
            String summary = aiWritingService.generateContent(prompt, "chapter_summary");
            
            // 确保概括长度合适
            summary = trimSummaryToLength(summary, 200);
            
            logger.info("✅ 章节概括生成完成: 长度={}字", summary.length());
            return summary;
            
        } catch (Exception e) {
            logger.error("生成章节概括失败", e);
            // 返回fallback概括
            return generateFallbackSummary(chapter);
        }
    }
    
    /**
     * 生成章节概括（使用前端传递的AI配置）
     * 将章节内容压缩为100-200字的简短概括
     * @param chapter 章节对象
     * @param aiConfig AI配置（来自前端）
     * @return 章节概括
     */
    public String generateChapterSummary(Chapter chapter, com.novel.dto.AIConfigRequest aiConfig) {
        logger.info("📝 开始生成章节概括（使用前端配置）: 章节ID={}, 章节号={}, provider={}", 
                   chapter.getId(), chapter.getChapterNumber(), aiConfig.getProvider());
        
        // 验证AI配置
        if (aiConfig == null || !aiConfig.isValid()) {
            logger.warn("AI配置无效，使用fallback概括");
            return generateFallbackSummary(chapter);
        }
        
        try {
            String content = chapter.getContent();
            if (content == null || content.trim().isEmpty()) {
                return "本章暂无内容";
            }
            
            // 构建概括提示词
            String prompt = buildSummaryPrompt(chapter);
            
            // 调用AI生成概括（使用同步非流式方式）
            String summary = callAIForSummary(prompt, aiConfig);
            
            // 确保概括长度合适
            summary = trimSummaryToLength(summary, 200);
            
            logger.info("✅ 章节概括生成完成: 长度={}字", summary.length());
            return summary;
            
        } catch (Exception e) {
            logger.error("生成章节概括失败", e);
            // 返回fallback概括
            return generateFallbackSummary(chapter);
        }
    }
    
    /**
     * 保存章节概括到数据库
     */
    public void saveChapterSummary(Long novelId, Integer chapterNumber, String summary) {
        try {
            // 检查是否已存在
            Optional<ChapterSummary> existing = chapterSummaryRepository.findByNovelIdAndChapterNumber(novelId, chapterNumber);
            
            ChapterSummary chapterSummary;
            if (existing.isPresent()) {
                chapterSummary = existing.get();
                chapterSummary.setSummary(summary);
                chapterSummary.setUpdatedAt(new Date());
                // 已存在则更新
                chapterSummaryRepository.updateById(chapterSummary);
            } else {
                chapterSummary = new ChapterSummary();
                chapterSummary.setNovelId(novelId);
                chapterSummary.setChapterNumber(chapterNumber);
                chapterSummary.setSummary(summary);
                chapterSummary.setCreatedAt(new Date());
                chapterSummary.setUpdatedAt(new Date());
                // 不存在则插入
                chapterSummaryRepository.insert(chapterSummary);
            }

            logger.info("💾 章节概括已保存: 小说ID={}, 章节={}", novelId, chapterNumber);

        } catch (Exception e) {
            logger.error("保存章节概括失败", e);
        }
    }

    /**
     * 获取最近N章的概括列表
     * 用于AI写作时提供前置章节信息
     */
    public List<String> getRecentChapterSummaries(Long novelId, Integer currentChapter, Integer count) {
        logger.info("📚 获取前置章节概括: 小说ID={}, 当前章节={}, 获取数量={}", novelId, currentChapter, count);
        
        try {
            // 计算起始章节
            int startChapter = Math.max(1, currentChapter - count);
            int endChapter = currentChapter - 1;
            
            if (endChapter < startChapter) {
                return new ArrayList<>();
            }
            
            // 从数据库获取概括
            List<ChapterSummary> summaries = chapterSummaryRepository.findByNovelIdAndChapterNumberBetween(
                novelId, startChapter, endChapter);
            
            // 按章节号排序并提取概括文本
            List<String> summaryTexts = summaries.stream()
                .sorted(Comparator.comparing(ChapterSummary::getChapterNumber))
                .map(ChapterSummary::getSummary)
                .collect(Collectors.toList());
            
            logger.info("✅ 获取到{}章概括", summaryTexts.size());
            return summaryTexts;
            
        } catch (Exception e) {
            logger.error("获取章节概括失败", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 获取指定章节的概括
     */
    public String getChapterSummary(Long novelId, Integer chapterNumber) {
        try {
            Optional<ChapterSummary> summary = chapterSummaryRepository.findByNovelIdAndChapterNumber(novelId, chapterNumber);
            return summary.map(ChapterSummary::getSummary).orElse("");
        } catch (Exception e) {
            logger.error("获取单章概括失败", e);
            return "";
        }
    }
    
    /**
     * 批量生成缺失的章节概括
     */
    public void generateMissingSummaries(Long novelId, List<Chapter> chapters) {
        logger.info("🔄 开始批量生成缺失的章节概括: 小说ID={}, 章节数={}", novelId, chapters.size());
        
        int generatedCount = 0;
        for (Chapter chapter : chapters) {
            try {
                // 检查是否已存在概括
                Optional<ChapterSummary> existing = chapterSummaryRepository.findByNovelIdAndChapterNumber(
                    novelId, chapter.getChapterNumber());
                
                if (!existing.isPresent()) {
                    String summary = generateChapterSummary(chapter);
                    saveChapterSummary(novelId, chapter.getChapterNumber(), summary);
                    generatedCount++;
                    
                    // 避免AI调用过频
                    Thread.sleep(1000);
                }
                
            } catch (Exception e) {
                logger.warn("生成第{}章概括失败: {}", chapter.getChapterNumber(), e.getMessage());
            }
        }
        
        logger.info("✅ 批量生成完成，共生成{}个章节概括", generatedCount);
    }
    
    /**
     * 删除指定章节的概括
     */
    public void deleteChapterSummary(Long novelId, Integer chapterNumber) {
        try {
            Optional<ChapterSummary> existing = chapterSummaryRepository.findByNovelIdAndChapterNumber(novelId, chapterNumber);
            if (existing.isPresent()) {
                chapterSummaryRepository.deleteById(existing.get().getId());
                logger.info("🗑️ 已删除章节概括: 小说ID={}, 章节={}", novelId, chapterNumber);
            } else {
                logger.debug("章节概括不存在，无需删除: 小说ID={}, 章节={}", novelId, chapterNumber);
            }
        } catch (Exception e) {
            logger.error("删除章节概括失败: 小说ID={}, 章节={}", novelId, chapterNumber, e);
            throw new RuntimeException("删除章节概括失败", e);
        }
    }
    
    /**
     * 获取小说的完整章节概括报告
     */
    public Map<String, Object> getNovelSummaryReport(Long novelId) {
        try {
            List<ChapterSummary> allSummaries = chapterSummaryRepository.findByNovelIdOrderByChapterNumber(novelId);
            
            Map<String, Object> report = new HashMap<>();
            report.put("totalChapters", allSummaries.size());
            report.put("averageSummaryLength", allSummaries.stream()
                .mapToInt(s -> s.getSummary().length())
                .average().orElse(0.0));
            
            // 按章节号分组的概括
            Map<Integer, String> summaryMap = allSummaries.stream()
                .collect(Collectors.toMap(
                    ChapterSummary::getChapterNumber,
                    ChapterSummary::getSummary));
            report.put("summaries", summaryMap);
            
            return report;
            
        } catch (Exception e) {
            logger.error("获取小说概括报告失败", e);
            return new HashMap<>();
        }
    }
    
    // ================================
    // 私有辅助方法
    // ================================
    
    /**
     * 构建概括生成的提示词
     */
    private String buildSummaryPrompt(Chapter chapter) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("请基于以下章节内容，输出一段简洁的中文概括：\n");
        prompt.append("- 仅描述本章大致发生了什么（主要情节/关键人物/起因-经过-结果的顺序概述）；\n");
        prompt.append("- 80-150字为宜；\n");
        prompt.append("- 不要推测伏笔或后续发展，不要分析评价，不要列点或加标题，不要使用Markdown；\n");
        prompt.append("- 只输出一段纯文本概括。\n\n");

        prompt.append("章节标题：").append(chapter.getTitle()).append("\n");
        prompt.append("章节内容：\n").append(chapter.getContent()).append("\n\n");

        prompt.append("概括：");
        
        return prompt.toString();
    }
    
    /**
     * 修剪概括长度
     */
    private String trimSummaryToLength(String summary, int maxLength) {
        if (summary == null) return "";
        
        summary = summary.trim();
        if (summary.length() <= maxLength) {
            return summary;
        }
        
        // 尝试在句号处截断
        int lastPeriod = summary.lastIndexOf('。', maxLength);
        if (lastPeriod > maxLength * 0.7) { // 如果句号位置不算太靠前
            return summary.substring(0, lastPeriod + 1);
        }
        
        // 否则直接截断并添加省略号
        return summary.substring(0, maxLength - 3) + "...";
    }
    
    /**
     * 使用AIConfigRequest调用AI生成概括（同步方式）
     */
    @SuppressWarnings("unchecked")
    private String callAIForSummary(String prompt, com.novel.dto.AIConfigRequest aiConfig) throws Exception {
        String apiUrl = aiConfig.getApiUrl();
        String apiKey = aiConfig.getApiKey();
        String model = aiConfig.getModel();
        
        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 500);
        requestBody.put("temperature", 0.7);
        requestBody.put("stream", false);
        
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        messages.add(message);
        requestBody.put("messages", messages);
        
        // 发送HTTP请求
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
        restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory());
        
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        
        org.springframework.http.HttpEntity<Map<String, Object>> entity = 
            new org.springframework.http.HttpEntity<>(requestBody, headers);
        
        logger.info("调用AI生成章节概括: {}", apiUrl);
        org.springframework.http.ResponseEntity<String> response = 
            restTemplate.postForEntity(apiUrl, entity, String.class);
        
        // 解析响应
        String responseBody = response.getBody();
        if (responseBody == null) {
            throw new RuntimeException("AI响应为空");
        }
        
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String, Object> responseMap = mapper.readValue(responseBody, Map.class);
        
        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("AI响应格式错误：无choices字段");
        }
        
        Map<String, Object> firstChoice = choices.get(0);
        Map<String, Object> messageData = (Map<String, Object>) firstChoice.get("message");
        if (messageData == null) {
            throw new RuntimeException("AI响应格式错误：无message字段");
        }
        
        String content = (String) messageData.get("content");
        if (content == null || content.trim().isEmpty()) {
            throw new RuntimeException("AI返回内容为空");
        }
        
        return content.trim();
    }
    
    /**
     * 生成fallback概括（当AI生成失败时）
     */
    private String generateFallbackSummary(Chapter chapter) {
        String content = chapter.getContent();
        if (content == null || content.trim().isEmpty()) {
            return "第" + chapter.getChapterNumber() + "章暂无内容";
        }
        
        // 简单提取前200字作为概括
        String fallback = content.trim();
        if (fallback.length() > 200) {
            fallback = fallback.substring(0, 197) + "...";
        }
        
        return "第" + chapter.getChapterNumber() + "章：" + fallback;
    }
    
    /**
     * 异步生成并保存章节概要（用于优化用户体验）
     * 在生成当前章节内容后，后台异步提取上一章的概要
     * 
     * @param novelId 小说ID
     * @param chapterNumber 章节号
     * @param aiConfig AI配置
     */
    public void generateAndSaveChapterSummaryAsync(Long novelId, Integer chapterNumber, 
                                                    com.novel.dto.AIConfigRequest aiConfig) {
        try {
            // 查找章节
            com.novel.domain.entity.Chapter chapter = findChapterByNumber(novelId, chapterNumber);
            if (chapter == null) {
                logger.warn("章节不存在: novelId={}, chapterNumber={}", novelId, chapterNumber);
                return;
            }
            
            // 检查是否已有概要
            Optional<ChapterSummary> existing = chapterSummaryRepository.findByNovelIdAndChapterNumber(
                novelId, chapterNumber
            );
            
            if (existing.isPresent()) {
                logger.debug("章节概要已存在，跳过生成: novelId={}, chapterNumber={}", novelId, chapterNumber);
                return;
            }
            
            // 生成概要
            String summary = generateChapterSummary(chapter, aiConfig);
            
            // 保存概要
            saveChapterSummary(novelId, chapterNumber, summary);
            
            logger.info("✅ 异步生成并保存章节概要成功: novelId={}, chapterNumber={}", novelId, chapterNumber);
            
        } catch (Exception e) {
            logger.error("异步生成章节概要失败: novelId={}, chapterNumber={}", novelId, chapterNumber, e);
            // 不抛出异常，避免影响主流程
        }
    }
    
    /**
     * 根据章节号查找章节
     */
    private com.novel.domain.entity.Chapter findChapterByNumber(Long novelId, Integer chapterNumber) {
        try {
            return chapterRepository.findByNovelAndChapterNumber(novelId, chapterNumber);
        } catch (Exception e) {
            logger.error("查找章节失败: novelId={}, chapterNumber={}", novelId, chapterNumber, e);
            return null;
        }
    }
}