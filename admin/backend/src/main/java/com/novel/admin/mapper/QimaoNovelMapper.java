package com.novel.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.admin.dto.QimaoNovelDTO;
import com.novel.admin.entity.QimaoNovel;
import org.apache.ibatis.annotations.*;

@Mapper
public interface QimaoNovelMapper extends BaseMapper<QimaoNovel> {

    @Select("<script>" +
            "SELECT id, title as novelTitle, author, category, " +
            "0 as chapterCount, status, " +
            "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') as scrapedAt, " +
            "cover_image_url as coverUrl, description, word_count as wordCount " +
            "FROM qimao_novels " +
            "<where>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (title LIKE CONCAT('%', #{keyword}, '%') OR author LIKE CONCAT('%', #{keyword}, '%'))" +
            "</if>" +
            "<if test='category != null and category != \"\"'>" +
            "AND category = #{category}" +
            "</if>" +
            "<if test='status != null and status != \"\"'>" +
            "AND status = #{status}" +
            "</if>" +
            "</where>" +
            "ORDER BY created_at DESC" +
            "</script>")
    Page<QimaoNovelDTO> selectQimaoNovelPage(Page<QimaoNovelDTO> page, 
                                              @Param("keyword") String keyword,
                                              @Param("category") String category,
                                              @Param("status") String status);

    @Select("SELECT id, title as novelTitle, author, category, " +
            "0 as chapterCount, status, " +
            "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') as scrapedAt, " +
            "cover_image_url as coverUrl, description, word_count as wordCount " +
            "FROM qimao_novels WHERE id = #{id}")
    QimaoNovelDTO selectQimaoNovelById(@Param("id") Long id);
    
    @Select("SELECT COUNT(*) FROM qimao_novels")
    Integer getTotalCount();
    
    @Select("SELECT COUNT(*) FROM qimao_novels")
    Integer getTotalChapterCount();
    
    @Select("SELECT COUNT(*) FROM qimao_novels WHERE DATE(created_at) = CURDATE()")
    Integer getTodayCount();
}
