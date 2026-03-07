package com.novel.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.admin.dto.NovelDTO;
import com.novel.admin.entity.Novel;
import org.apache.ibatis.annotations.*;

@Mapper
public interface NovelMapper extends BaseMapper<Novel> {

    @Select("<script>" +
            "SELECT n.id, n.title, u.username as author, n.genre, n.status, " +
            "n.chapter_count as chapterCount, " +
            "n.word_count as wordCount, n.created_at as createdAt, n.updated_at as updatedAt " +
            "FROM novels n " +
            "LEFT JOIN users u ON n.created_by = u.id " +
            "<where>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (n.title LIKE CONCAT('%', #{keyword}, '%') OR u.username LIKE CONCAT('%', #{keyword}, '%'))" +
            "</if>" +
            "</where>" +
            "ORDER BY n.created_at DESC" +
            "</script>")
    Page<NovelDTO> selectNovelPage(Page<NovelDTO> page, @Param("keyword") String keyword);

    @Select("SELECT n.id, n.title, u.username as author, n.genre, n.status, " +
            "n.chapter_count as chapterCount, " +
            "n.word_count as wordCount, n.created_at as createdAt, n.updated_at as updatedAt " +
            "FROM novels n " +
            "LEFT JOIN users u ON n.created_by = u.id " +
            "WHERE n.id = #{id}")
    NovelDTO selectNovelById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM chapters WHERE novel_id = #{novelId}")
    Integer getChapterCount(@Param("novelId") Long novelId);

    @Select("SELECT COALESCE(SUM(word_count), 0) FROM chapters WHERE novel_id = #{novelId}")
    Integer getWordCount(@Param("novelId") Long novelId);

    @Select("SELECT COUNT(*) FROM characters WHERE novel_id = #{novelId}")
    Integer getCharacterCount(@Param("novelId") Long novelId);
}
