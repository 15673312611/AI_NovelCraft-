package com.novel.mapper;

import com.novel.entity.WritingVersionHistory;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 写作版本历史 Mapper
 */
@Mapper
public interface WritingVersionHistoryMapper {

    /**
     * 创建版本记录
     */
    @Insert("INSERT INTO writing_version_history (" +
            "novel_id, chapter_id, document_id, source_type, content, word_count, diff_ratio" +
            ") VALUES (" +
            "#{novelId}, #{chapterId}, #{documentId}, #{sourceType}, #{content}, #{wordCount}, #{diffRatio}" +
            ")")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WritingVersionHistory history);

    /**
     * 获取章节的最近一个版本
     */
    @Select("SELECT * FROM writing_version_history " +
            "WHERE chapter_id = #{chapterId} " +
            "ORDER BY created_at DESC LIMIT 1")
    WritingVersionHistory findLatestByChapterId(@Param("chapterId") Long chapterId);

    /**
     * 获取文档的最近一个版本
     */
    @Select("SELECT * FROM writing_version_history " +
            "WHERE document_id = #{documentId} " +
            "ORDER BY created_at DESC LIMIT 1")
    WritingVersionHistory findLatestByDocumentId(@Param("documentId") Long documentId);

    /**
     * 获取章节的版本历史（按时间倒序，限制条数）
     */
    @Select("SELECT * FROM writing_version_history " +
            "WHERE chapter_id = #{chapterId} " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    List<WritingVersionHistory> findByChapterId(@Param("chapterId") Long chapterId,
                                                @Param("limit") int limit);

    /**
     * 获取文档的版本历史（按时间倒序，限制条数）
     */
    @Select("SELECT * FROM writing_version_history " +
            "WHERE document_id = #{documentId} " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    List<WritingVersionHistory> findByDocumentId(@Param("documentId") Long documentId,
                                                 @Param("limit") int limit);

    /**
     * 删除章节多余的历史版本，只保留最近的若干条
     */
    @Delete("DELETE FROM writing_version_history " +
            "WHERE chapter_id = #{chapterId} AND id NOT IN (" +
            "  SELECT id FROM (" +
            "    SELECT id FROM writing_version_history " +
            "    WHERE chapter_id = #{chapterId} " +
            "    ORDER BY created_at DESC LIMIT #{keep}" +
            "  ) AS t" +
            ")")
    int deleteOldByChapterId(@Param("chapterId") Long chapterId, @Param("keep") int keep);

    /**
     * 删除文档多余的历史版本，只保留最近的若干条
     */
    @Delete("DELETE FROM writing_version_history " +
            "WHERE document_id = #{documentId} AND id NOT IN (" +
            "  SELECT id FROM (" +
            "    SELECT id FROM writing_version_history " +
            "    WHERE document_id = #{documentId} " +
            "    ORDER BY created_at DESC LIMIT #{keep}" +
            "  ) AS t" +
            ")")
    int deleteOldByDocumentId(@Param("documentId") Long documentId, @Param("keep") int keep);
}
