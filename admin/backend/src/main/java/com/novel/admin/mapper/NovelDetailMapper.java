package com.novel.admin.mapper;

import com.novel.admin.entity.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 小说详情 Mapper
 * 根据正确的数据库表结构实现
 */
@Mapper
public interface NovelDetailMapper {

    /**
     * 获取小说基本信息
     */
    @Select("SELECT * FROM novels WHERE id = #{novelId}")
    Novel selectNovelById(@Param("novelId") Long novelId);

    /**
     * 获取小说大纲
     */
    @Select("SELECT * FROM novel_outlines WHERE novel_id = #{novelId} LIMIT 1")
    NovelOutline selectOutlineByNovelId(@Param("novelId") Long novelId);

    /**
     * 获取分卷列表
     */
    @Select("SELECT * FROM novel_volumes WHERE novel_id = #{novelId} ORDER BY volume_number")
    List<NovelVolume> selectVolumesByNovelId(@Param("novelId") Long novelId);

    /**
     * 获取章纲列表（按小说ID，支持分页和状态筛选）
     */
    @Select("<script>" +
            "SELECT * FROM volume_chapter_outlines " +
            "WHERE novel_id = #{novelId} " +
            "<if test='status != null'> AND status = #{status} </if>" +
            "ORDER BY global_chapter_number " +
            "<if test='offset != null and limit != null'> LIMIT #{offset}, #{limit} </if>" +
            "</script>")
    List<VolumeChapterOutline> selectChapterOutlinesByNovelId(
            @Param("novelId") Long novelId,
            @Param("status") String status,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit
    );

    /**
     * 获取章纲列表（按卷ID）
     */
    @Select("SELECT * FROM volume_chapter_outlines " +
            "WHERE volume_id = #{volumeId} " +
            "ORDER BY chapter_in_volume")
    List<VolumeChapterOutline> selectChapterOutlinesByVolumeId(@Param("volumeId") Long volumeId);

    /**
     * 获取所有章纲（不分页）
     */
    @Select("SELECT * FROM volume_chapter_outlines " +
            "WHERE novel_id = #{novelId} " +
            "ORDER BY global_chapter_number")
    List<VolumeChapterOutline> selectAllChapterOutlinesByNovelId(@Param("novelId") Long novelId);

    /**
     * 获取章节列表（支持分页和状态筛选，不返回content字段以提高性能）
     */
    @Select("<script>" +
            "SELECT id, title, subtitle, simple_content, order_num, status, word_count, " +
            "chapter_number, summary, notes, is_public, published_at, reading_time_minutes, " +
            "previous_chapter_id, next_chapter_id, novel_id, created_at, updated_at " +
            "FROM chapters " +
            "WHERE novel_id = #{novelId} " +
            "<if test='status != null'> AND status = #{status} </if>" +
            "ORDER BY order_num " +
            "<if test='offset != null and limit != null'> LIMIT #{offset}, #{limit} </if>" +
            "</script>")
    List<Chapter> selectChaptersByNovelId(
            @Param("novelId") Long novelId,
            @Param("status") String status,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit
    );

    /**
     * 获取所有章节（不分页，不返回content）
     */
    @Select("SELECT id, title, subtitle, simple_content, order_num, status, word_count, " +
            "chapter_number, summary, notes, is_public, published_at, reading_time_minutes, " +
            "previous_chapter_id, next_chapter_id, novel_id, created_at, updated_at " +
            "FROM chapters " +
            "WHERE novel_id = #{novelId} " +
            "ORDER BY order_num")
    List<Chapter> selectAllChaptersByNovelId(@Param("novelId") Long novelId);

    /**
     * 获取章节详细内容（包含content字段）
     */
    @Select("SELECT * FROM chapters WHERE id = #{chapterId}")
    Chapter selectChapterDetailById(@Param("chapterId") Long chapterId);
    
    /**
     * 根据ID获取章节（别名方法）
     */
    @Select("SELECT * FROM chapters WHERE id = #{chapterId}")
    Chapter selectChapterById(@Param("chapterId") Long chapterId);
    
    /**
     * 更新章节
     */
    @org.apache.ibatis.annotations.Update("<script>" +
            "UPDATE chapters SET " +
            "<if test='title != null'>title = #{title},</if>" +
            "<if test='subtitle != null'>subtitle = #{subtitle},</if>" +
            "<if test='content != null'>content = #{content},</if>" +
            "<if test='simpleContent != null'>simple_content = #{simpleContent},</if>" +
            "<if test='summary != null'>summary = #{summary},</if>" +
            "<if test='notes != null'>notes = #{notes},</if>" +
            "<if test='status != null'>status = #{status},</if>" +
            "<if test='wordCount != null'>word_count = #{wordCount},</if>" +
            "<if test='isPublic != null'>is_public = #{isPublic},</if>" +
            "<if test='readingTimeMinutes != null'>reading_time_minutes = #{readingTimeMinutes},</if>" +
            "updated_at = NOW() " +
            "WHERE id = #{id}" +
            "</script>")
    int updateChapter(Chapter chapter);

    /**
     * 获取小说统计信息
     */
    @Select("SELECT " +
            "n.word_count as totalWords, " +
            "n.chapter_count as totalChapters, " +
            "(SELECT COUNT(*) FROM novel_volumes WHERE novel_id = #{novelId}) as totalVolumes, " +
            "(SELECT COUNT(*) FROM volume_chapter_outlines WHERE novel_id = #{novelId} AND status = 'WRITTEN') as writtenChapterOutlines, " +
            "(SELECT COUNT(*) FROM volume_chapter_outlines WHERE novel_id = #{novelId} AND status = 'PENDING') as pendingChapterOutlines, " +
            "CASE WHEN n.target_total_chapters > 0 THEN ROUND((n.chapter_count / n.target_total_chapters) * 100, 2) ELSE 0 END as completionRate, " +
            "CASE WHEN n.chapter_count > 0 THEN ROUND(n.word_count / n.chapter_count, 0) ELSE 0 END as averageChapterWords " +
            "FROM novels n " +
            "WHERE n.id = #{novelId}")
    Map<String, Object> selectNovelStatistics(@Param("novelId") Long novelId);

    // ========== 兼容旧方法（用于AdminNovelService） ==========
    
    /**
     * 获取小说详情（兼容旧方法）
     */
    @Select("SELECT n.id, n.title, u.username as author, n.genre, n.status, " +
            "n.word_count as wordCount, n.chapter_count as chapterCount, " +
            "n.description, n.created_at as createdAt, n.updated_at as updatedAt " +
            "FROM novels n " +
            "LEFT JOIN users u ON n.created_by = u.id " +
            "WHERE n.id = #{novelId}")
    Map<String, Object> getNovelDetail(@Param("novelId") Long novelId);

    /**
     * 获取最新大纲（兼容旧方法）
     */
    @Select("SELECT id, novel_id as novelId, title, status, " +
            "target_chapter_count as targetChapterCount, core_theme as coreTheme, " +
            "plot_structure as plotStructure, created_at as createdAt " +
            "FROM novel_outlines " +
            "WHERE novel_id = #{novelId} " +
            "ORDER BY created_at DESC LIMIT 1")
    Map<String, Object> getLatestOutlineByNovelId(@Param("novelId") Long novelId);

    /**
     * 获取卷列表（兼容旧方法）
     */
    @Select("SELECT id, novel_id as novelId, title, volume_number as volumeNumber, " +
            "chapter_start as chapterStart, chapter_end as chapterEnd, " +
            "status, theme, description, content_outline as contentOutline, " +
            "estimated_word_count as estimatedWordCount, actual_word_count as actualWordCount, " +
            "created_at as createdAt, updated_at as updatedAt " +
            "FROM novel_volumes " +
            "WHERE novel_id = #{novelId} " +
            "ORDER BY volume_number ASC")
    List<Map<String, Object>> getVolumesByNovelId(@Param("novelId") Long novelId);

    /**
     * 获取章节列表（兼容旧方法）
     */
    @Select("SELECT id, novel_id as novelId, title, chapter_number as orderNum, " +
            "status, word_count as wordCount, " +
            "created_at as createdAt, updated_at as updatedAt " +
            "FROM chapters " +
            "WHERE novel_id = #{novelId} " +
            "ORDER BY chapter_number ASC")
    List<Map<String, Object>> getChaptersByNovelId(@Param("novelId") Long novelId);

    /**
     * 获取角色列表（兼容旧方法）
     */
    @Select("SELECT id, novel_id as novelId, name, character_type as characterType, " +
            "status, appearance_count as appearanceCount, description " +
            "FROM characters " +
            "WHERE novel_id = #{novelId} " +
            "ORDER BY appearance_count DESC")
    List<Map<String, Object>> getCharactersByNovelId(@Param("novelId") Long novelId);

    /**
     * 获取世界观词典（兼容旧方法）
     */
    @Select("SELECT id, novel_id as novelId, term, type, " +
            "first_mention as firstMention, description, context_info as contextInfo, " +
            "usage_count as usageCount, is_important as isImportant, " +
            "created_time as createdTime, updated_time as updatedTime " +
            "FROM novel_world_dictionary " +
            "WHERE novel_id = #{novelId} " +
            "ORDER BY is_important DESC, usage_count DESC")
    List<Map<String, Object>> getWorldviewByNovelId(@Param("novelId") Long novelId);
}
