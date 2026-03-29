package com.novel.service;

import com.novel.domain.entity.Chapter;
import com.novel.domain.entity.ChapterSummary;
import com.novel.dto.AIConfigRequest;
import com.novel.repository.ChapterSummaryRepository;
import com.novel.service.prompt.ManagedPromptService;
import com.novel.service.prompt.SystemPromptDescriptor;
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
    
    @Autowired(required = false)
    private com.novel.agentic.service.graph.IGraphService graphService;

    @Autowired
    private ManagedPromptService managedPromptService;

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
            if (summary == null || summary.trim().isEmpty()) {
                // AI可能返回空，使用fallback
                return generateFallbackSummary(chapter);
            }
            
            // 确保概括长度合适
            summary = trimSummaryToLength(summary, 200);

            // 🆕 解析并保存Summary Signals（只保存结构化键值，不做任意写入）
            try {
                Map<String, String> signals = parseSummarySignals(summary);
                if (!signals.isEmpty() && graphService != null) {
                    graphService.addSummarySignals(chapter.getNovelId(), chapter.getChapterNumber(), signals);
                }
            } catch (Exception ex) {
                logger.warn("解析Summary Signals失败（忽略）: {}", ex.getMessage());
            }
            
            logger.info("✅ 章节概括生成完成: 长度={}字", summary.length());
            return summary;
            
        } catch (Exception e) {
            logger.warn("生成章节概括失败，使用fallback概括", e);
            // 返回fallback概括
            return generateFallbackSummary(chapter);
        }
    }

    /**
     * 🆕 解析“Summary Signals: key=val; key=val”行为结构化Map
     */
    private Map<String, String> parseSummarySignals(String summary) {
        Map<String, String> result = new java.util.HashMap<>();
        if (summary == null) return result;
        String[] lines = summary.split("\r?\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.toLowerCase().startsWith("summary signals:")) {
                String payload = line.substring(line.indexOf(':') + 1).trim();
                String[] pairs = payload.split(";");
                for (String pair : pairs) {
                    String p = pair.trim();
                    if (p.isEmpty()) continue;
                    int idx = p.indexOf('=');
                    if (idx <= 0) continue;
                    String k = p.substring(0, idx).trim();
                    String v = p.substring(idx + 1).trim();
                    if (!k.isEmpty()) result.put(k, v);
                }
                break;
            }
        }
        return result;
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

    public void generateOrUpdateSummary(Chapter chapter, AIConfigRequest aiConfig) {
        if (chapter == null || chapter.getNovelId() == null || chapter.getChapterNumber() == null) {
            return;
        }
        try {
            String summary;
            if (aiConfig != null && aiConfig.isValid()) {
                summary = generateChapterSummary(chapter, aiConfig);
            } else {
                summary = generateChapterSummary(chapter);
            }
            saveChapterSummary(chapter.getNovelId(), chapter.getChapterNumber(), summary);
        } catch (Exception e) {
            logger.warn("章节概括生成失败: novelId={}, chapter={}", chapter.getNovelId(), chapter.getChapterNumber(), e);
            String fallback = generateFallbackSummary(chapter);
            saveChapterSummary(chapter.getNovelId(), chapter.getChapterNumber(), fallback);
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
     * 构建概括提示词
     */
    private String buildSummaryPrompt(Chapter chapter) {
        String optimizedContent = ChapterTextOptimizer.sampleForAi(chapter.getContent(), 8000);
        return renderManagedPrompt(
            "chapter.summary.basic",
            "章节概要提取提示词",
            "system_chain",
            "根据章节正文生成高信息密度的剧情摘要，并附带 Summary Signals 结构化状态信号",
            220,
            List.of("chapter_title", "chapter_content"),
            """
                你是一位顶尖网文编辑。请为下面这一章生成150-250字的剧情摘要，像“追更提醒”一样高密度、强钩子、可复盘。

                【写作目标】只保留对“理解剧情走向”和“承接下一章”必要的信息。

                【必须覆盖的4点（自然融入一段内，不要打标签）】
                1) 动作与结果：最关键的“行为→后果”一句。
                2) 情报增量：本章新增的重要信息/设定。
                3) 关系/立场变化：人物关系或冲突格局的显著变动（若无写“无”）。
                4) 悬念钩子：促使读者读下一章的未决点。

                【状态信号（务必从正文中提取，若无则写“无”）】在摘要末尾另起一行输出“Summary Signals:”后接半角分号分隔的键值：
                loc=当前位置; realm=境界变动; item=关键物品变动; foreshadow=埋/回收/无; deaths=死亡角色(可空); relChange=关系变动(可空)

                【硬性规则】
                - 一段成文，不要分点、不要加任何标题或解释。
                - 不要剧透下一章；只基于当前章节内容。
                - 用语要快节奏、具体、少形容词，避免空话套话。

                ---
                章节标题：{{chapter_title}}
                章节内容：
                {{chapter_content}}
                ---
                请现在输出摘要正文，其后紧跟一行“Summary Signals: ...”。
                """,
            Map.of(
                "chapter_title", safeText(chapter.getTitle()),
                "chapter_content", safeText(optimizedContent)
            )
        );
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
        // 走统一的AI服务，保证与其他请求一致（非流式、带超时、统一解析）
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> msg = new HashMap<>();
        msg.put("role", "user");
        msg.put("content", prompt);
        messages.add(msg);

        String content = aiWritingService.generateContentWithMessages(messages, "content_summarization", aiConfig);
        if (content == null) {
            throw new RuntimeException("AI返回内容为空");
        }
        // 去除可能的<think>噪声
        content = content.replaceAll("<think>.*?</think>", "");
        content = content.replaceAll("<think>.*", "");
        content = content.replaceAll(".*</think>", "");
        content = content.trim();
        if (content.isEmpty()) {
            throw new RuntimeException("AI返回内容为空");
        }
        return content;
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
     * 🆕 获取最近N章的概括（返回包含章节号的Map，供上下文构建使用）
     */
    public List<Map<String, Object>> getRecentSummaries(Long novelId, Integer currentChapter, int limit) {
        logger.info("📚 获取前置章节概括（含章节号）: 小说ID={}, 当前章节={}, 获取数量={}", novelId, currentChapter, limit);
        
        try {
            // 计算起始章节
            int startChapter = Math.max(1, currentChapter - limit);
            int endChapter = currentChapter - 1;
            
            if (endChapter < startChapter) {
                return new ArrayList<>();
            }
            
            // 从数据库获取概括
            List<ChapterSummary> summaries = chapterSummaryRepository.findByNovelIdAndChapterNumberBetween(
                novelId, startChapter, endChapter);
            
            // 按章节号排序并转换为Map
            List<Map<String, Object>> result = summaries.stream()
                .sorted(Comparator.comparing(ChapterSummary::getChapterNumber))
                .map(summary -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("chapterNumber", summary.getChapterNumber());
                    map.put("summary", summary.getSummary());
                    return map;
                })
                .collect(Collectors.toList());
            
            logger.info("✅ 获取到{}章概括（含章节号）", result.size());
            return result;
            
        } catch (Exception e) {
            logger.error("获取章节概括失败", e);
            return new ArrayList<>();
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

    private String renderManagedPrompt(String promptKey,
                                       String name,
                                       String category,
                                       String description,
                                       int sortOrder,
                                       List<String> variables,
                                       String template,
                                       Map<String, ?> values) {
        return managedPromptService.render(SystemPromptDescriptor.builder()
                        .promptKey(promptKey)
                        .name(name)
                        .category(category)
                        .description(description)
                        .variables(variables)
                        .sortOrder(sortOrder)
                        .defaultContent(template)
                        .build(),
                values);
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}
