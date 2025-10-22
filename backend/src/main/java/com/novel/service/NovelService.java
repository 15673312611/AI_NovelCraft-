package com.novel.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.domain.entity.Novel;
import com.novel.domain.entity.User;
import com.novel.repository.NovelRepository;
import com.novel.repository.UserRepository;
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
        return novelRepository.deleteById(id) > 0;
    }

    /**
     * 获取小说列表
     */
    public IPage<Novel> getNovels(int page, int size) {
        Page<Novel> pageParam = new Page<>(page + 1, size);
        return novelRepository.selectPage(pageParam, null);
    }

    /**
     * 根据作者获取小说列表
     */
    public IPage<Novel> getNovelsByAuthor(Long authorId, int page, int size) {
        Page<Novel> pageParam = new Page<>(page + 1, size);
        return novelRepository.findByAuthorId(authorId, pageParam);
    }

    /**
     * 搜索小说
     */
    public IPage<Novel> searchNovels(String keyword, int page, int size) {
        Page<Novel> pageParam = new Page<>(page + 1, size);
        return novelRepository.searchByKeyword(keyword, pageParam);
    }

    /**
     * 根据ID获取小说
     */
    public Novel getNovelById(Long id) {
        return novelRepository.selectById(id);
    }

    /**
     * 搜索用户小说
     */
    public List<Novel> searchUserNovels(Long userId, String query, String genre, String status) {
        QueryWrapper<Novel> queryWrapper = new QueryWrapper<>();
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
     * 获取用户小说列表（带分页和排序）
     */
    public List<Novel> getNovelsByUser(Long userId, String sortBy, String sortOrder) {
        QueryWrapper<Novel> queryWrapper = new QueryWrapper<>();
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
	 * 获取可用于写作的书籍列表
	 * 条件：已确认大纲的书籍（不包括OUTLINE_PENDING状态）
	 */
	public List<Novel> getWritableNovels(Long userId) {
		com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Novel> wrapper = 
			new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
		
		wrapper.eq("created_by", userId)
			   .isNotNull("outline")  // 必须有大纲
			   .ne("creation_stage", "OUTLINE_PENDING")  // 排除待确认大纲状态
			   .orderByDesc("updated_at");
		
		List<Novel> novels = novelRepository.selectList(wrapper);
		
		// 进一步筛选：确保大纲不为空字符串
		return novels.stream()
			.filter(novel -> novel.getOutline() != null && !novel.getOutline().trim().isEmpty())
			.collect(java.util.stream.Collectors.toList());
	}
}