package com.novel.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.domain.entity.PromptTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提示词模板Repository
 */
@Mapper
public interface PromptTemplateRepository extends BaseMapper<PromptTemplate> {
}

