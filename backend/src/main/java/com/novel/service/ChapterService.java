package com.novel.service;

import com.novel.agentic.service.graph.IGraphService;
import com.novel.domain.entity.Chapter;
import com.novel.domain.entity.Novel;
import com.novel.repository.ChapterRepository;
import com.novel.repository.NovelRepository;
import com.novel.common.security.AuthUtils;
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
    private NovelRepository novelRepository;
    
    @Autowired
    private com.novel.repository.NovelTemplateProgressRepository templateProgressRepository;
    
    @Autowired
    @SuppressWarnings("unused")
    private NovelFolderService folderService;

    @Autowired
    private WritingVersionHistoryService writingVersionHistoryService;

    @Autowired(required = false)
    private IGraphService graphService;


    /**
     * 初始化第一章（写作工作室用）
     */
    @Transactional
    public Chapter initFirstChapter(Long novelId) {
        logger.info("初始化小说ID={}的第一章", novelId);
        
        // 检查是否已有第一章
        Chapter existing = chapterRepository.findByNovelAndChapterNumber(novelId, 1);
        if (existing != null) {
            logger.info("小说ID={}的第一章已存在，跳过创建", novelId);
            return existing;
        }
        
        // 创建第一章
        Chapter chapter = new Chapter();
        chapter.setNovelId(novelId);
        chapter.setChapterNumber(1);
        chapter.setTitle("开篇");  // 只存章节名
        chapter.setContent("");
        chapter.setWordCount(0);
        chapter.setStatus(Chapter.ChapterStatus.DRAFT);
        chapter.setIsPublic(false);
        
        chapterRepository.insert(chapter);
        logger.info("小说ID={}的第一章创建成功，章节ID={}", novelId, chapter.getId());
        
        return chapter;
    }

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
        if (chapter.getGenerationContext() == null) {
            chapter.setGenerationContext(null);
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

    public Chapter getChapterByNovelAndNumber(Long novelId, Integer chapterNumber) {
        if (novelId == null || chapterNumber == null) {
            return null;
        }
        return chapterRepository.findByNovelAndChapterNumber(novelId, chapterNumber);
    }

    public List<Chapter> getRecentChapters(Long novelId, Integer currentChapter, int limit) {
        if (novelId == null || limit <= 0) {
            return java.util.Collections.emptyList();
        }

        List<Chapter> chapters;
        if (currentChapter == null || currentChapter <= 1) {
            chapters = chapterRepository.findLatestChapterByNovel(novelId, limit);
        } else {
            chapters = chapterRepository.findChaptersBefore(novelId, currentChapter);
            if (chapters.size() > limit) {
                chapters = chapters.subList(0, limit);
            }
        }

        return chapters;
    }

    /**
     * 获取小说已存在的最新章节号（如果不存在则返回null）
     */
    public Integer getLastChapterNumber(Long novelId) {
        if (novelId == null) {
            return null;
        }
        List<Chapter> latest = chapterRepository.findLatestChapterByNovel(novelId, 1);
        if (latest == null || latest.isEmpty()) {
            return null;
        }
        return latest.get(0).getChapterNumber();
    }

    /**
     * 计算下一章节号（如果还没有章节，则返回1）
     */
    public Integer getNextChapterNumber(Long novelId) {
        Integer last = getLastChapterNumber(novelId);
        if (last == null || last < 1) {
            return 1;
        }
        return last + 1;
    }

    /**
     * 更新章节（重写章节时会清理相关旧数据）
     */
    public Chapter updateChapter(Long id, Chapter chapterData) {
        return updateChapterInternal(id, chapterData, true);
    }

    /**
     * 系统内部使用，跳过登录校验的章节更新（供Agentic流程等后台任务调用）
     */
    public Chapter updateChapterInternal(Long id, Chapter chapterData) {
        return updateChapterInternal(id, chapterData, false);
    }

    private Chapter updateChapterInternal(Long id, Chapter chapterData, boolean validateAuth) {
        Long currentUserId = AuthUtils.getCurrentUserId();
        if (validateAuth && currentUserId == null) {
            throw new SecurityException("用户未登录，无法更新章节");
        }

        Chapter chapter = chapterRepository.selectById(id);
        if (chapter == null) {
            return null;
        }

        Novel novel = novelRepository.selectById(chapter.getNovelId());
        if (novel == null) {
            throw new SecurityException("小说不存在，无法更新章节");
        }
        if (validateAuth) {
            if (novel.getAuthorId() == null || !currentUserId.equals(novel.getAuthorId())) {
                throw new SecurityException("无权限修改该章节");
            }
        }

        String oldContentSnapshot = chapter.getContent() != null ? chapter.getContent() : "";
        String newContentForHistory = null;
        boolean isContentRewrite = false;

        if (chapterData.getTitle() != null) {
            chapter.setTitle(chapterData.getTitle());
        }
        if (chapterData.getContent() != null) {
            String oldContent = chapter.getContent() != null ? chapter.getContent() : "";
            String newContent = chapterData.getContent();

            if (!oldContent.isEmpty() && !oldContent.equals(newContent)) {
                double similarity = calculateSimilarity(oldContent, newContent);
                if (similarity < 0.5) {
                    isContentRewrite = true;
                    logger.info("检测到章节 {} 内容被重写（相似度：{}%），将清理相关旧数据",
                            chapter.getChapterNumber(), (int) (similarity * 100));
                }
            }

            chapter.setContent(newContent);
            chapter.setWordCount(newContent.length());
            chapter.calculateReadingTime();

            newContentForHistory = newContent;
        }
        if (chapterData.getChapterNumber() != null) {
            chapter.setChapterNumber(chapterData.getChapterNumber());
        }
        if (chapterData.getStatus() != null) {
            chapter.setStatus(chapterData.getStatus());
        }
        if (chapterData.getGenerationContext() != null) {
            chapter.setGenerationContext(chapterData.getGenerationContext());
        }

        chapterRepository.updateById(chapter);

        if (isContentRewrite && chapter.getNovelId() != null && chapter.getChapterNumber() != null) {
            cleanupChapterRelatedData(chapter.getNovelId(), chapter.getChapterNumber());
        }

        if (newContentForHistory != null) {
            try {
                writingVersionHistoryService.recordChapterVersion(
                        chapter,
                        oldContentSnapshot,
                        newContentForHistory,
                        "AUTO_SAVE"
                );
            } catch (Exception e) {
                logger.warn("记录章节版本历史失败（不影响章节更新）: {}", e.getMessage());
            }
        }

        return chapter;
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
        Long currentUserId = AuthUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new SecurityException("用户未登录，无法删除章节");
        }

        Chapter chapter = chapterRepository.selectById(id);
        if (chapter == null) {
            return false;
        }

        Novel novel = novelRepository.selectById(chapter.getNovelId());
        if (novel == null || novel.getAuthorId() == null || !currentUserId.equals(novel.getAuthorId())) {
            throw new SecurityException("无权限删除该章节");
        }

        Long novelId = chapter.getNovelId();
        Integer chapterNumber = chapter.getChapterNumber();

        if (novelId != null && chapterNumber != null) {
            try {
                chapterSummaryService.deleteChapterSummary(novelId, chapterNumber);
            } catch (Exception e) {
                logger.warn("删除章节时清理章节概括失败: novelId={}, chapterNumber={}, error={}", novelId, chapterNumber, e.getMessage());
            }

            if (graphService != null) {
                try {
                    graphService.deleteChapterEntities(novelId, chapterNumber);
                } catch (Exception e) {
                    logger.warn("删除章节时清理图谱数据失败: novelId={}, chapterNumber={}, error={}", novelId, chapterNumber, e.getMessage());
                }
            }
        }

        return chapterRepository.deleteById(id) > 0;
    }

    public List<Chapter> getChapterMetadataByNovel(Long novelId) {
        return chapterRepository.findMetadataByNovel(novelId);
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
     * 根据小说获取章节列表（分页，优化版，排除content等大文本字段）
     */
    public IPage<Chapter> getChaptersByNovel(Long novelId, int page, int size) {
        Page<Chapter> pageParam = new Page<>(page + 1, size);
        return chapterRepository.findMetadataByNovelPaged(novelId, pageParam);
    }

    /**
     * 发布章节（使用后端配置 - 已弃用）
     * @deprecated 建议使用 {@link #publishChapter(Long, com.novel.dto.AIConfigRequest)}
     */
    @Deprecated
    public Chapter publishChapter(Long id) {
        Long currentUserId = AuthUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new SecurityException("用户未登录，无法发布章节");
        }

        Chapter chapter = chapterRepository.selectById(id);
        if (chapter == null) {
            return null;
        }

        Novel novel = novelRepository.selectById(chapter.getNovelId());
        if (novel == null || novel.getAuthorId() == null || !currentUserId.equals(novel.getAuthorId())) {
            throw new SecurityException("无权限发布该章节");
        }
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
        Long currentUserId = AuthUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new SecurityException("用户未登录，无法发布章节");
        }

        Chapter chapter = chapterRepository.selectById(id);
        if (chapter == null) {
            return null;
        }

        Novel novel = novelRepository.selectById(chapter.getNovelId());
        if (novel == null || novel.getAuthorId() == null || !currentUserId.equals(novel.getAuthorId())) {
            throw new SecurityException("无权限发布该章节");
        }
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
        Long currentUserId = AuthUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new SecurityException("用户未登录，无法取消发布章节");
        }

        Chapter chapter = chapterRepository.selectById(id);
        if (chapter == null) {
            return null;
        }

        Novel novel = novelRepository.selectById(chapter.getNovelId());
        if (novel == null || novel.getAuthorId() == null || !currentUserId.equals(novel.getAuthorId())) {
            throw new SecurityException("无权限取消发布该章节");
        }
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
