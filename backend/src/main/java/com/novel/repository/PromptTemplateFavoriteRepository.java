package com.novel.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.domain.entity.PromptTemplateFavorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提示词模板收藏Repository
 */
@Mapper
public interface PromptTemplateFavoriteRepository extends BaseMapper<PromptTemplateFavorite> {
}

