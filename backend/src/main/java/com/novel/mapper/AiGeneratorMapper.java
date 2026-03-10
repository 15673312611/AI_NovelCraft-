package com.novel.mapper;

import com.novel.entity.AiGenerator;
import org.apache.ibatis.annotations.*;
import java.util.List;

/**
 * AI Generator Mapper
 */
@Mapper
public interface AiGeneratorMapper {

    /**
     * Lấy tất cả generator đang active
     */
    @Select("SELECT * FROM ai_generator WHERE status = 1 ORDER BY sort_order ASC, id ASC")
    List<AiGenerator> findAllActive();

    /**
     * Lấy generator theo ID
     */
    @Select("SELECT * FROM ai_generator WHERE id = #{id}")
    AiGenerator findById(@Param("id") Long id);
}

