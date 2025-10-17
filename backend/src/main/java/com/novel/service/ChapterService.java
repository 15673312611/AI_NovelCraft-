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
     * 更新章节
     */
    public Chapter updateChapter(Long id, Chapter chapterData) {
        Chapter chapter = chapterRepository.selectById(id);
        if (chapter != null) {
            if (chapterData.getTitle() != null) {
                chapter.setTitle(chapterData.getTitle());
            }
            if (chapterData.getContent() != null) {
                chapter.setContent(chapterData.getContent());
                chapter.setWordCount(chapterData.getContent().length());
                chapter.calculateReadingTime();
            }
            if (chapterData.getChapterNumber() != null) {
                chapter.setChapterNumber(chapterData.getChapterNumber());
            }
            if (chapterData.getStatus() != null) {
                chapter.setStatus(chapterData.getStatus());
            }

            chapterRepository.updateById(chapter);
            return chapter;
        }
        return null;
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
