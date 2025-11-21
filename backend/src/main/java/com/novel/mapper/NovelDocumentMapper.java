package com.novel.mapper;

import com.novel.entity.NovelDocument;
import org.apache.ibatis.annotations.*;
import java.util.List;

/**
 * 文档Mapper
 */
@Mapper
public interface NovelDocumentMapper {

    /**
     * 根据小说ID获取所有文档
     */
    @Select("SELECT * FROM novel_document WHERE novel_id = #{novelId} ORDER BY sort_order ASC, id ASC")
    List<NovelDocument> findByNovelId(@Param("novelId") Long novelId);

    /**
     * 根据文件夹ID获取文档列表
     */
    @Select("SELECT * FROM novel_document WHERE folder_id = #{folderId} ORDER BY sort_order ASC, id ASC")
    List<NovelDocument> findByFolderId(@Param("folderId") Long folderId);

    /**
     * 根据ID获取文档
     */
    @Select("SELECT * FROM novel_document WHERE id = #{id}")
    NovelDocument findById(@Param("id") Long id);

    /**
     * 根据文件夹和标题获取文档
     */
    @Select("SELECT * FROM novel_document WHERE folder_id = #{folderId} AND title = #{title} LIMIT 1")
    NovelDocument findByFolderAndTitle(@Param("folderId") Long folderId, @Param("title") String title);

    /**
     * 创建文档
     */
    @Insert("INSERT INTO novel_document (novel_id, folder_id, title, content, sort_order) " +
            "VALUES (#{novelId}, #{folderId}, #{title}, #{content}, #{sortOrder})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NovelDocument document);

    /**
     * 更新文档
     */
    @Update("UPDATE novel_document SET title = COALESCE(#{title}, title), folder_id = COALESCE(#{folderId}, folder_id), " +
            "content = COALESCE(#{content}, content), sort_order = COALESCE(#{sortOrder}, sort_order), " +
            "updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int update(NovelDocument document);

    /**
     * 删除文档
     */
    @Delete("DELETE FROM novel_document WHERE id = #{id}")
    int delete(@Param("id") Long id);

    /**
     * 更新排序
     */
    @Update("UPDATE novel_document SET sort_order = #{sortOrder} WHERE id = #{id}")
    int updateSortOrder(@Param("id") Long id, @Param("sortOrder") Integer sortOrder);

    /**
     * 统计小说的文档数量
     */
    @Select("SELECT COUNT(*) FROM novel_document WHERE novel_id = #{novelId}")
    int countByNovelId(@Param("novelId") Long novelId);

    /**
     * 搜索文档（包含章节和辅助文档）
     */
    @Select("SELECT id, novel_id, folder_id, title, content, document_type, word_count, sort_order, created_at, updated_at " +
            "FROM (" +
            "  SELECT id, novel_id, NULL as folder_id, title, content, 'chapter' as document_type, " +
            "         word_count, order_num AS sort_order, created_at, updated_at " +
            "  FROM chapters " +
            "  WHERE novel_id = #{novelId} AND (title LIKE CONCAT('%', #{keyword}, '%') OR content LIKE CONCAT('%', #{keyword}, '%')) " +
            "  UNION ALL " +
            "  SELECT NULL AS id, NULL AS novel_id, NULL AS folder_id, NULL AS title, NULL AS content, " +
            "         NULL AS document_type, NULL AS word_count, NULL AS sort_order, " +
            "         NULL AS created_at, NULL AS updated_at " +
            "  FROM DUAL WHERE 1 = 0 " +
            ") AS combined_results " +
            "ORDER BY updated_at DESC")
    List<NovelDocument> searchDocuments(@Param("novelId") Long novelId, @Param("keyword") String keyword);

    /**
     * 获取最近的章节（用于代理式AI写作）
     */
    @Select("SELECT * FROM novel_document WHERE novel_id = #{novelId} AND id < #{beforeChapter} " +
            "ORDER BY id DESC LIMIT #{limit}")
    List<NovelDocument> findRecentChaptersByNovelId(
        @Param("novelId") Long novelId, 
        @Param("beforeChapter") Integer beforeChapter, 
        @Param("limit") Integer limit);
}

