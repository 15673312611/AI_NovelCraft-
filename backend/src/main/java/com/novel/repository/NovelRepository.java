package com.novel.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.domain.entity.Novel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NovelRepository extends BaseMapper<Novel> {

    /**
     * 根据作者ID查询小说列表（完整字段，已弃用）
     * @deprecated 建议使用 {@link #findListByAuthorId} 以提升查询性能
     */
    @Deprecated
    @Select("SELECT * FROM novels WHERE created_by = #{authorId} ORDER BY updated_at DESC")
    IPage<Novel> findByAuthorId(@Param("authorId") Long authorId, Page<Novel> page);

    /**
     * 根据作者ID查询小说列表（排除outline等大文本字段，用于列表展示）
     */
    @Select("SELECT id, title, subtitle, description, cover_image_url, status, genre, tags, " +
            "target_audience, word_count, chapter_count, estimated_completion, started_at, " +
            "completed_at, is_public, rating, rating_count, target_total_chapters, " +
            "words_per_chapter, planned_volume_count, total_word_target, creation_stage, " +
            "created_by, created_at, updated_at " +
            "FROM novels WHERE created_by = #{authorId} ORDER BY updated_at DESC")
    IPage<Novel> findListByAuthorId(@Param("authorId") Long authorId, Page<Novel> page);

    @Select("SELECT * FROM novels WHERE status = #{status}")
    IPage<Novel> findByStatus(@Param("status") String status, Page<Novel> page);

    @Select("SELECT * FROM novels WHERE genre = #{genre}")
    IPage<Novel> findByGenre(@Param("genre") String genre, Page<Novel> page);

    @Select("SELECT * FROM novels WHERE title LIKE CONCAT('%', #{keyword}, '%') OR description LIKE CONCAT('%', #{keyword}, '%')")
    IPage<Novel> searchByKeyword(@Param("keyword") String keyword, Page<Novel> page);

    @Select("SELECT COUNT(*) FROM novels WHERE created_by = #{authorId}")
    long countByAuthorId(@Param("authorId") Long authorId);

    @Select("SELECT COUNT(*) FROM novels WHERE status = #{status}")
    long countByStatus(@Param("status") String status);
}

