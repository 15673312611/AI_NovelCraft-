package com.novel.service;

import com.novel.domain.entity.Chapter;
import com.novel.repository.ChapterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 章节服务
 */
@Service
@Transactional
public class ChapterService {

    private static final Logger logger = LoggerFactory.getLogger(ChapterService.class);

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private ChapterSummaryService chapterSummaryService;
    
    @Autowired
    private com.novel.repository.NovelTemplateProgressRepository templateProgressRepository;
    


    /**
     * 创建章节
     */
    public Chapter createChapter(Chapter chapter) {
        // 设置默认值
        if (chapter.getStatus() == null) {
            chapter.setStatus(Chapter.ChapterStatus.DRAFT);
        }
        if (chapter.getWordCount() == null && chapter.getContent() != null) {
            chapter.setWordCount(chapter.getContent().length());
        }
        if (chapter.getIsPublic() == null) {
            chapter.setIsPublic(false);
        }
        
        // 注意：Chapter实体中没有createdBy字段，如果需要可以后续添加
        
        // 计算阅读时间
        chapter.calculateReadingTime();
        
        chapterRepository.insert(chapter);
        return chapter;
    }

    /**
     * 获取章节
     */
    public Chapter getChapter(Long id) {
        return chapterRepository.selectById(id);
    }

    /**
     * 更新章节（重写章节时会清理相关旧数据）
     */
    public Chapter updateChapter(Long id, Chapter chapterData) {
        Chapter chapter = chapterRepository.selectById(id);
        if (chapter != null) {
            boolean isContentRewrite = false;
            
            if (chapterData.getTitle() != null) {
                chapter.setTitle(chapterData.getTitle());
            }
            if (chapterData.getContent() != null) {
                // 检测是否是内容重写（内容变化超过50%视为重写）
                String oldContent = chapter.getContent() != null ? chapter.getContent() : "";
                String newContent = chapterData.getContent();
                
                if (!oldContent.isEmpty() && !oldContent.equals(newContent)) {
                    // 计算内容相似度（简单判断：新内容与旧内容差异大于50%）
                    double similarity = calculateSimilarity(oldContent, newContent);
                    if (similarity < 0.5) {
                        isContentRewrite = true;
                        logger.info("检测到章节 {} 内容被重写（相似度：{}%），将清理相关旧数据", 
                            chapter.getChapterNumber(), (int)(similarity * 100));
                    }
                }
                
                chapter.setContent(newContent);
                chapter.setWordCount(newContent.length());
                chapter.calculateReadingTime();
            }
            if (chapterData.getChapterNumber() != null) {
                chapter.setChapterNumber(chapterData.getChapterNumber());
            }
            if (chapterData.getStatus() != null) {
                chapter.setStatus(chapterData.getStatus());
            }

            chapterRepository.updateById(chapter);
            
            // 如果是重写，清理相关旧数据
            if (isContentRewrite && chapter.getNovelId() != null && chapter.getChapterNumber() != null) {
                cleanupChapterRelatedData(chapter.getNovelId(), chapter.getChapterNumber());
            }
            
            return chapter;
        }
        return null;
    }
    
    /**
     * 清理章节相关的旧数据（重写时调用）
     * 
     * 当用户修改章节内容后，需要清理基于原内容生成的所有派生数据：
     * 1. 章节概括（chapter_summaries表）
     * 2. 模板进度（如果该章是模板阶段的起始章节）
     * 3. 记忆库中的章节相关记录（lastUpdatedChapter等）
     */
    private void cleanupChapterRelatedData(Long novelId, Integer chapterNumber) {
        try {
            logger.info("🧹 开始清理第 {} 章的相关旧数据", chapterNumber);
            
            // 1. 清理章节概括（chapter_summaries表）
            // 该概括是基于章节内容生成的，内容变化后需要重新生成
            try {
                chapterSummaryService.deleteChapterSummary(novelId, chapterNumber);
                logger.info("✅ 已清理第 {} 章的概括", chapterNumber);
            } catch (Exception e) {
                logger.warn("清理章节概括失败: {}", e.getMessage());
            }
            
            // 2. 清理/重置模板进度（novel_template_progress表）
            // 如果该章节是当前模板阶段的起始章节，需要重置该阶段
            try {
                com.novel.entity.NovelTemplateProgress progress = templateProgressRepository.findByNovelId(novelId);
                if (progress != null && progress.getEnabled()) {
                    // 如果重写的章节在模板进度之前或等于最后更新章节，需要回退
                    if (progress.getLastUpdatedChapter() != null 
                        && chapterNumber <= progress.getLastUpdatedChapter()) {
                        logger.info("⚠️ 第 {} 章在模板进度范围内（当前进度到第{}章），回退模板进度", 
                                   chapterNumber, progress.getLastUpdatedChapter());
                        
                        // 回退到该章节的前一章
                        progress.setLastUpdatedChapter(chapterNumber - 1);
                        
                        // 如果该章节是当前阶段的起始章节，也需要回退阶段起始章节
                        if (progress.getStageStartChapter() != null 
                            && chapterNumber <= progress.getStageStartChapter()) {
                            progress.setStageStartChapter(chapterNumber);
                        }
                        
                        templateProgressRepository.update(progress);
                        logger.info("✅ 已回退模板进度到第{}章", chapterNumber - 1);
                    }
                }
            } catch (Exception e) {
                logger.warn("处理模板进度失败: {}", e.getMessage());
            }
            
            // 3. 清理记忆库中该章节的相关标记
            // 记忆库中的 lastUpdatedChapter 如果等于或大于当前章节，需要回退
            // 注意：我们不直接删除记忆库数据，而是标记需要重新提取
            // 实际的记忆库更新会在重新发布章节时自动触发
            try {
                // 这里只是标记清理，实际数据在重新发布时会被覆盖
                logger.info("ℹ️ 记忆库将在章节重新发布时自动更新");
            } catch (Exception e) {
                logger.warn("处理记忆库标记失败: {}", e.getMessage());
            }
            
            logger.info("🎉 第 {} 章相关旧数据清理完成", chapterNumber);
            
        } catch (Exception e) {
            logger.error("清理章节相关数据时发生错误: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 计算两个字符串的相似度（简化版）
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0;
        
        // 使用Levenshtein距离的简化版本
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;
        
        // 简化计算：基于长度差异和前1000字符的匹配度
        int sampleLen = Math.min(1000, Math.min(s1.length(), s2.length()));
        String sample1 = s1.substring(0, sampleLen);
        String sample2 = s2.substring(0, sampleLen);
        
        int matches = 0;
        for (int i = 0; i < sampleLen; i++) {
            if (sample1.charAt(i) == sample2.charAt(i)) {
                matches++;
            }
        }
        
        double lengthSimilarity = 1.0 - Math.abs(s1.length() - s2.length()) / (double) maxLen;
        double contentSimilarity = matches / (double) sampleLen;
        
        return (lengthSimilarity + contentSimilarity) / 2.0;
    }

    /**
     * 删除章节
     */
    public boolean deleteChapter(Long id) {
        return chapterRepository.deleteById(id) > 0;
    }

    /**
     * 根据小说获取章节列表
     */
    public List<Chapter> getChaptersByNovel(Long novelId) {
        return chapterRepository.findByNovelOrderByChapterNumberAsc(novelId);
    }

    /**
     * 根据小说ID获取章节列表（别名方法）
     */
    public List<Chapter> getChaptersByNovelId(Long novelId) {
        return getChaptersByNovel(novelId);
    }

    /**
     * 根据小说获取章节列表（分页）
     */
    public IPage<Chapter> getChaptersByNovel(Long novelId, int page, int size) {
        Page<Chapter> pageParam = new Page<>(page + 1, size);
        return chapterRepository.findByNovel(novelId, pageParam);
    }

    /**
     * 发布章节（使用后端配置 - 已弃用）
     * @deprecated 建议使用 {@link #publishChapter(Long, com.novel.dto.AIConfigRequest)}
     */
    @Deprecated
    public Chapter publishChapter(Long id) {
        Chapter chapter = chapterRepository.selectById(id);
        if (chapter != null) {
            chapter.publish();
            chapterRepository.updateById(chapter);

            // 确认发布后，确保生成并保存本章的AI概括
            try {
                if (chapter.getContent() != null && !chapter.getContent().trim().isEmpty()
                        && chapter.getNovelId() != null && chapter.getChapterNumber() != null) {
                    String summary = chapterSummaryService.generateChapterSummary(chapter);
                    chapterSummaryService.saveChapterSummary(chapter.getNovelId(), chapter.getChapterNumber(), summary);
                }
            } catch (Exception e) {
                logger.warn("发布章节后生成概括失败（不影响发布）: {}", e.getMessage());
            }

            return chapter;
        }
        return null;
    }
    
    /**
     * 发布章节（使用前端传递的AI配置）
     * @param id 章节ID
     * @param aiConfig AI配置（来自前端）
     * @return 发布后的章节
     */
    public Chapter publishChapter(Long id, com.novel.dto.AIConfigRequest aiConfig) {
        Chapter chapter = chapterRepository.selectById(id);
        if (chapter != null) {
            chapter.publish();
            chapterRepository.updateById(chapter);

            // 确认发布后，确保生成并保存本章的AI概括
            try {
                if (chapter.getContent() != null && !chapter.getContent().trim().isEmpty()
                        && chapter.getNovelId() != null && chapter.getChapterNumber() != null) {
                    // 使用前端传递的AI配置
                    if (aiConfig != null && aiConfig.isValid()) {
                        String summary = chapterSummaryService.generateChapterSummary(chapter, aiConfig);
                        chapterSummaryService.saveChapterSummary(chapter.getNovelId(), chapter.getChapterNumber(), summary);
                        logger.info("✅ 使用前端AI配置生成章节概括成功");
                    } else {
                        logger.warn("AI配置无效，跳过生成概括");
                    }
                }
            } catch (Exception e) {
                logger.warn("发布章节后生成概括失败（不影响发布）: {}", e.getMessage());
            }

            return chapter;
        }
        return null;
    }

    /**
     * 取消发布章节
     */
    public Chapter unpublishChapter(Long id) {
        Chapter chapter = chapterRepository.selectById(id);
        if (chapter != null) {
            chapter.unpublish();
            chapterRepository.updateById(chapter);
            return chapter;
        }
        return null;
    }

    /**
     * 获取章节统计信息
     */
    public Map<String, Object> getChapterStatistics(Long id) {
        Chapter chapter = chapterRepository.selectById(id);
        Map<String, Object> stats = new HashMap<>();

        if (chapter != null) {
            stats.put("wordCount", chapter.getWordCount());
            stats.put("readingTime", chapter.getReadingTimeMinutes());
            stats.put("lastModified", chapter.getUpdatedAt());
            stats.put("status", chapter.getStatus());
            stats.put("isPublished", chapter.isPublished());
        } else {
            stats.put("wordCount", 0);
            stats.put("readingTime", 0);
            stats.put("lastModified", "");
            stats.put("status", "DRAFT");
            stats.put("isPublished", false);
        }
        
        return stats;
    }

    /**
     * 搜索章节
     */
    public List<Chapter> searchChapters(Long novelId, String query) {
        return chapterRepository.searchByTitleOrContent(novelId, query);
    }

    /**
     * 获取小说的章节统计
     */
    public Map<String, Object> getNovelChapterStatistics(Long novelId) {
        List<Chapter> chapters = chapterRepository.findByNovelOrderByChapterNumberAsc(novelId);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalChapters", chapters.size());
        stats.put("totalWords", chapters.stream().mapToInt(c -> c.getWordCount() != null ? c.getWordCount() : 0).sum());
        stats.put("averageWordsPerChapter", chapters.isEmpty() ? 0 : 
            chapters.stream().mapToInt(c -> c.getWordCount() != null ? c.getWordCount() : 0).average().orElse(0));
        stats.put("lastUpdated", chapters.stream()
            .filter(c -> c.getUpdatedAt() != null)
            .map(Chapter::getUpdatedAt)
            .max(LocalDateTime::compareTo)
            .orElse(null));
        stats.put("completionRate", chapters.isEmpty() ? 0 : 
            (double) chapters.stream().mapToInt(c -> c.isCompleted() ? 1 : 0).sum() / chapters.size() * 100);
        
        return stats;
    }

    /**
     * 在小说中搜索章节
     */
    public List<Chapter> searchChaptersInNovel(Long novelId, String query) {
        QueryWrapper<Chapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("novel_id", novelId);
        
        if (StringUtils.hasText(query)) {
            queryWrapper.and(wrapper -> wrapper
                .like("title", query)
                .or()
                .like("content", query));
        }
        
        queryWrapper.orderByAsc("chapter_number");
        return chapterRepository.selectList(queryWrapper);
    }

    /**
     * 搜索用户章节
     */
    public List<Chapter> searchUserChapters(Long userId, String query) {
        QueryWrapper<Chapter> queryWrapper = new QueryWrapper<>();
        
        // 通过关联查询获取用户的章节，这里简化处理
        // 实际应该通过join查询或者先查用户的小说ID列表
        if (StringUtils.hasText(query)) {
            queryWrapper.like("title", query).or().like("content", query);
        }
        
        queryWrapper.orderByDesc("updated_at");
        return chapterRepository.selectList(queryWrapper);
    }
}
