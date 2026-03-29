package com.novel.mapper;

import com.novel.entity.AiGenerator;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * AI Generator Mapper
 */
@Mapper
public interface AiGeneratorMapper {

    /**
     * List active generators with public fields only.
     */
    @Select("SELECT id, name, description, icon, category, sort_order, status, created_at, updated_at " +
            "FROM ai_generator WHERE status = 1 ORDER BY sort_order ASC, id ASC")
    List<AiGenerator> findAllActive();

    /**
     * Get generator public info by id.
     */
    @Select("SELECT id, name, description, icon, category, sort_order, status, created_at, updated_at " +
            "FROM ai_generator WHERE id = #{id}")
    AiGenerator findPublicById(@Param("id") Long id);

    /**
     * Get generator with internal prompt content.
     */
    @Select("SELECT * FROM ai_generator WHERE id = #{id}")
    AiGenerator findById(@Param("id") Long id);
}
