package com.novel.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.domain.entity.Novel;
import com.novel.domain.entity.User;
import com.novel.repository.NovelRepository;
import com.novel.repository.UserRepository;
import com.novel.common.security.AuthUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 小说服务
 */
@Service
@Transactional
public class NovelService {

    @Autowired
    private NovelRepository novelRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * 创建小说（需要传入用户ID）
     */
    public Novel createNovel(Novel novel, Long userId) {
        // 设置默认值
        if (novel.getStatus() == null) {
            novel.setStatus(Novel.NovelStatus.DRAFT);
        }
        if (novel.getWordCount() == null) {
            novel.setWordCount(0);
        }
        if (novel.getChapterCount() == null) {
            novel.setChapterCount(0);
        }
        if (novel.getStartedAt() == null) {
            novel.setStartedAt(LocalDateTime.now());
        }

        // 设置作者ID为当前登录用户
        novel.setAuthorId(userId);

        // 验证用户是否存在
        User author = userRepository.selectById(userId);
        if (author == null) {
            throw new RuntimeException("用户不存在，无法创建小说。用户ID: " + userId);
        }

        try {
            novelRepository.insert(novel);
            return novel;
        } catch (Exception e) {
            // 如果是外键约束错误，提供更友好的错误信息
            if (e.getMessage().contains("foreign key constraint fails") && e.getMessage().contains("author_id")) {
                throw new RuntimeException("创建小说失败：指定的作者不存在。请确保数据库中有ID为 " + novel.getAuthorId() + " 的用户。", e);
            }
            throw e;
        }
    }



    /**
     * 获取小说
     */
    public Novel getNovel(Long id) {
        return novelRepository.selectById(id);
    }

    /**
     * 更新小说
     */
    public Novel updateNovel(Long id, Novel novelData) {
        Novel existingNovel = novelRepository.selectById(id);
        if (existingNovel != null) {
            if (novelData.getTitle() != null) {
                existingNovel.setTitle(novelData.getTitle());
            }
            if (novelData.getDescription() != null) {
                existingNovel.setDescription(novelData.getDescription());
            }
            if (novelData.getGenre() != null) {
                existingNovel.setGenre(novelData.getGenre());
            }
            if (novelData.getStatus() != null) {
                existingNovel.setStatus(novelData.getStatus());
            }
            // 新增：支持更新大纲
            if (novelData.getOutline() != null) {
                existingNovel.setOutline(novelData.getOutline());
            }
            // 新增：支持更新计划卷数
            if (novelData.getPlannedVolumeCount() != null) {
                existingNovel.setPlannedVolumeCount(novelData.getPlannedVolumeCount());
            }
            // 新增：支持更新目标章节数
            if (novelData.getTargetTotalChapters() != null) {
                existingNovel.setTargetTotalChapters(novelData.getTargetTotalChapters());
            }
            // 新增：支持更新每章字数
            if (novelData.getWordsPerChapter() != null) {
                existingNovel.setWordsPerChapter(novelData.getWordsPerChapter());
            }
            // 新增：支持更新目标总字数
            if (novelData.getTotalWordTarget() != null) {
                existingNovel.setTotalWordTarget(novelData.getTotalWordTarget());
            }
            novelRepository.updateById(existingNovel);
            return existingNovel;
        }
        return null;
    }

    /**
     * 删除小说
     */
    public boolean deleteNovel(Long id) {
        // 仅允许小说作者删除自己的小说
        Long currentUserId = AuthUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new SecurityException("用户未登录，无法删除小说");
        }

        Novel novel = novelRepository.selectById(id);
        if (novel == null) {
            return false;
        }

        if (novel.getAuthorId() == null || !currentUserId.equals(novel.getAuthorId())) {
            throw new SecurityException("无权限删除该小说");
        }

        return novelRepository.deleteById(id) > 0;
    }

    // 小说列表查询所需的字段（排除outline等大文本字段）
    private static final String[] NOVEL_LIST_FIELDS = {
        "id", "title", "subtitle", "description", "cover_image_url", "status", "genre", "tags",
        "target_audience", "word_count", "chapter_count", "estimated_completion", "started_at",
        "completed_at", "is_public", "rating", "rating_count", "target_total_chapters",
        "words_per_chapter", "planned_volume_count", "total_word_target", "creation_stage",
        "created_by", "created_at", "updated_at"
    };

    /**
     * 获取小说列表（优化版，排除outline等大文本字段）
     */
    public IPage<Novel> getNovels(int page, int size) {
        Page<Novel> pageParam = new Page<>(page + 1, size);
        QueryWrapper<Novel> queryWrapper = new QueryWrapper<>();
        queryWrapper.select(NOVEL_LIST_FIELDS);
        queryWrapper.orderByDesc("updated_at");
        return novelRepository.selectPage(pageParam, queryWrapper);
    }

    /**
     * 根据作者获取小说列表（优化版，排除outline等大文本字段）
     */
    public IPage<Novel> getNovelsByAuthor(Long authorId, int page, int size) {
        Page<Novel> pageParam = new Page<>(page + 1, size);
        return novelRepository.findListByAuthorId(authorId, pageParam);
    }

    /**
     * 搜索小说
     */
    public IPage<Novel> searchNovels(String keyword, int page, int size) {
        Page<Novel> pageParam = new Page<>(page + 1, size);
        return novelRepository.searchByKeyword(keyword, pageParam);
    }
    /**
     * 根据 ID获取小说
     */
    public Novel getNovelById(Long id) {
        return novelRepository.selectById(id);
    }

    /**
     * 搜索用户小说（优化版，排除outline等大文本字段）
     */
    public List<Novel> searchUserNovels(Long userId, String query, String genre, String status) {
        QueryWrapper<Novel> queryWrapper = new QueryWrapper<>();
        queryWrapper.select(NOVEL_LIST_FIELDS);
        queryWrapper.eq("author_id", userId);
        
        if (StringUtils.hasText(query)) {
            queryWrapper.and(wrapper -> wrapper
                .like("title", query)
                .or()
                .like("description", query));
        }
        
        if (StringUtils.hasText(genre)) {
            queryWrapper.eq("genre", genre);
        }
        
        if (StringUtils.hasText(status)) {
            queryWrapper.eq("status", status);
        }
        
        queryWrapper.orderByDesc("updated_at");
        return novelRepository.selectList(queryWrapper);
    }

    /**
     * 获取用户小说列表（带分页和排序，优化版，排除outline等大文本字段）
     */
    public List<Novel> getNovelsByUser(Long userId, String sortBy, String sortOrder) {
        QueryWrapper<Novel> queryWrapper = new QueryWrapper<>();
        queryWrapper.select(NOVEL_LIST_FIELDS);
        queryWrapper.eq("author_id", userId);
        
        // 处理排序
        if (StringUtils.hasText(sortBy)) {
            if ("desc".equalsIgnoreCase(sortOrder)) {
                queryWrapper.orderByDesc(sortBy);
            } else {
                queryWrapper.orderByAsc(sortBy);
            }
        } else {
            queryWrapper.orderByDesc("updated_at");
        }
        
        return novelRepository.selectList(queryWrapper);
    }

	/**
	 * 根据ID获取小说（兼容旧调用）
	 */
	public Novel getById(Long id) {
		return novelRepository.selectById(id);
	}

	/**
	 * 更新小说（简单版本，兼容旧调用）
	 */
	public Novel update(Novel novel) {
		if (novel == null || novel.getId() == null) {
			throw new IllegalArgumentException("小说对象或ID不能为空");
		}
		novelRepository.updateById(novel);
		return novel;
	}

	/**
	 * 更新小说的创作阶段
	 */
	public Novel updateCreationStage(Long novelId, Novel.CreationStage stage) {
		Novel novel = getById(novelId);
		if (novel == null) {
			throw new RuntimeException("小说不存在: " + novelId);
		}

		novel.setCreationStage(stage);
		novelRepository.updateById(novel);

		return novel;
	}

	/**
	 * 获取可用于写作的书籍列表（优化版，排除outline大文本字段）
	 * 条件：已确认大纲的书籍（不包括OUTLINE_PENDING状态）
	 */
	public List<Novel> getWritableNovels(Long userId) {
		com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Novel> wrapper = 
			new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
		
		// 优化查询：只查询列表展示所需字段，排除outline大字段
		wrapper.select(NOVEL_LIST_FIELDS)
			   .eq("created_by", userId)
			   .isNotNull("outline")  // 必须有大纲
			   .ne("outline", "")  // 确保大纲不为空字符串
			   .ne("creation_stage", "OUTLINE_PENDING")  // 排除待确认大纲状态
			   .orderByDesc("updated_at");
		
		return novelRepository.selectList(wrapper);
	}
}
