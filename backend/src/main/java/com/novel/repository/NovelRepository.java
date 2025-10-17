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

    @Select("SELECT * FROM novels WHERE created_by = #{authorId}")
    IPage<Novel> findByAuthorId(@Param("authorId") Long authorId, Page<Novel> page);

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

