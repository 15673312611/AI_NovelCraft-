package com.novel.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.domain.entity.Chapter;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface ChapterRepository extends BaseMapper<Chapter> {
    @Select("SELECT * FROM chapters WHERE novel_id = #{novelId} ORDER BY chapter_number ASC")
    List<Chapter> findByNovelOrderByChapterNumberAsc(@Param("novelId") Long novelId);

    @Select("SELECT * FROM chapters WHERE novel_id = #{novelId}")
    IPage<Chapter> findByNovel(@Param("novelId") Long novelId, Page<Chapter> page);

    @Select("SELECT * FROM chapters WHERE novel_id = #{novelId} AND status = #{status}")
    List<Chapter> findByNovelAndStatus(@Param("novelId") Long novelId, @Param("status") String status);

    @Select("SELECT * FROM chapters WHERE novel_id = #{novelId} AND is_public = #{isPublic}")
    List<Chapter> findByNovelAndIsPublic(@Param("novelId") Long novelId, @Param("isPublic") Boolean isPublic);

    @Select("SELECT * FROM chapters WHERE novel_id = #{novelId} AND is_public = true ORDER BY chapter_number ASC")
    List<Chapter> findPublishedChaptersByNovel(@Param("novelId") Long novelId);

    @Select("SELECT * FROM chapters WHERE novel_id = #{novelId} AND (LOWER(title) LIKE LOWER(CONCAT('%', #{query}, '%')) OR LOWER(content) LIKE LOWER(CONCAT('%', #{query}, '%'))) ORDER BY chapter_number ASC")
    List<Chapter> searchByTitleOrContent(@Param("novelId") Long novelId, @Param("query") String query);

    @Select("SELECT COUNT(*) FROM chapters WHERE novel_id = #{novelId}")
    long countByNovel(@Param("novelId") Long novelId);

    @Select("SELECT COUNT(*) FROM chapters WHERE novel_id = #{novelId} AND is_public = #{isPublic}")
    long countByNovelAndIsPublic(@Param("novelId") Long novelId, @Param("isPublic") Boolean isPublic);

    @Select("SELECT COALESCE(SUM(word_count), 0) FROM chapters WHERE novel_id = #{novelId}")
    Integer sumWordCountByNovel(@Param("novelId") Long novelId);

    @Select("SELECT COALESCE(SUM(word_count), 0) FROM chapters WHERE novel_id = #{novelId} AND is_public = true")
    Integer sumPublishedWordCountByNovel(@Param("novelId") Long novelId);

    @Select("SELECT * FROM chapters WHERE novel_id = #{novelId} ORDER BY chapter_number DESC LIMIT #{limit}")
    List<Chapter> findLatestChapterByNovel(@Param("novelId") Long novelId, @Param("limit") int limit);

    @Select("SELECT * FROM chapters WHERE novel_id = #{novelId} ORDER BY chapter_number ASC LIMIT #{limit}")
    List<Chapter> findFirstChapterByNovel(@Param("novelId") Long novelId, @Param("limit") int limit);

    @Select("SELECT * FROM chapters WHERE novel_id = #{novelId} AND chapter_number = #{chapterNumber}")
    Chapter findByNovelAndChapterNumber(@Param("novelId") Long novelId, @Param("chapterNumber") Integer chapterNumber);

    @Select("SELECT * FROM chapters WHERE novel_id = #{novelId} AND chapter_number > #{chapterNumber} ORDER BY chapter_number ASC")
    List<Chapter> findChaptersAfter(@Param("novelId") Long novelId, @Param("chapterNumber") Integer chapterNumber);

    @Select("SELECT * FROM chapters WHERE novel_id = #{novelId} AND chapter_number < #{chapterNumber} ORDER BY chapter_number DESC")
    List<Chapter> findChaptersBefore(@Param("novelId") Long novelId, @Param("chapterNumber") Integer chapterNumber);

    @Select("SELECT * FROM chapters WHERE novel_id = #{novelId} ORDER BY updated_at DESC LIMIT #{limit}")
    List<Chapter> findRecentlyUpdatedByNovel(@Param("novelId") Long novelId, @Param("limit") int limit);

    @Select("SELECT * FROM chapters WHERE novel_id = #{novelId} AND status = 'DRAFT' ORDER BY chapter_number ASC")
    List<Chapter> findDraftChaptersByNovel(@Param("novelId") Long novelId);

    @Select("SELECT * FROM chapters WHERE novel_id = #{novelId} AND status = 'COMPLETED' ORDER BY chapter_number ASC")
    List<Chapter> findCompletedChaptersByNovel(@Param("novelId") Long novelId);

    @Delete("DELETE FROM chapters WHERE novel_id = #{novelId}")
    int deleteByNovelId(@Param("novelId") Long novelId);
    
    @Select("SELECT * FROM chapters WHERE novel_id = #{novelId} AND chapter_number BETWEEN #{startChapter} AND #{endChapter} ORDER BY chapter_number ASC")
    List<Chapter> findByNovelIdAndChapterNumberBetween(@Param("novelId") Long novelId, @Param("startChapter") Integer startChapter, @Param("endChapter") Integer endChapter);
    
    @Select("SELECT * FROM chapters WHERE novel_id = #{novelId} ORDER BY chapter_number ASC")
    List<Chapter> findByNovelIdOrderByChapterNumberAsc(@Param("novelId") Long novelId);

    @Select("SELECT id, title, chapter_number, status, word_count, is_public, novel_id, created_at, updated_at FROM chapters WHERE novel_id = #{novelId} ORDER BY chapter_number ASC")
    List<Chapter> findMetadataByNovel(@Param("novelId") Long novelId);
}

