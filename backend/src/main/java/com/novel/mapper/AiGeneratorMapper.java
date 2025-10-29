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
     * Lấy tất cả generator theo category
     */
    @Select("SELECT * FROM ai_generator WHERE status = 1 AND category = #{category} ORDER BY sort_order ASC, id ASC")
    List<AiGenerator> findByCategory(@Param("category") String category);

    /**
     * Lấy generator theo ID
     */
    @Select("SELECT * FROM ai_generator WHERE id = #{id}")
    AiGenerator findById(@Param("id") Long id);

    /**
     * Thêm generator mới
     */
    @Insert("INSERT INTO ai_generator (name, description, icon, prompt, category, sort_order, status) " +
            "VALUES (#{name}, #{description}, #{icon}, #{prompt}, #{category}, #{sortOrder}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AiGenerator generator);

    /**
     * Cập nhật generator
     */
    @Update("UPDATE ai_generator SET name = #{name}, description = #{description}, " +
            "icon = #{icon}, prompt = #{prompt}, category = #{category}, " +
            "sort_order = #{sortOrder}, status = #{status} WHERE id = #{id}")
    int update(AiGenerator generator);

    /**
     * Xóa generator (soft delete)
     */
    @Update("UPDATE ai_generator SET status = 0 WHERE id = #{id}")
    int delete(@Param("id") Long id);

    /**
     * Lấy tất cả generator (bao gồm inactive)
     */
    @Select("SELECT * FROM ai_generator ORDER BY sort_order ASC, id ASC")
    List<AiGenerator> findAll();
}

