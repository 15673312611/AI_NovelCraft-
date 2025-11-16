package com.novel.mapper;

import com.novel.entity.ChapterAnalysis;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ChapterAnalysisMapper {
    
    @Insert("INSERT INTO chapter_analysis (novel_id, analysis_type, start_chapter, end_chapter, analysis_content, word_count) " +
            "VALUES (#{novelId}, #{analysisType}, #{startChapter}, #{endChapter}, #{analysisContent}, #{wordCount})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChapterAnalysis analysis);
    
    @Select("SELECT * FROM chapter_analysis WHERE id = #{id}")
    ChapterAnalysis findById(@Param("id") Long id);
    
    @Select("SELECT * FROM chapter_analysis WHERE novel_id = #{novelId} ORDER BY created_at DESC")
    List<ChapterAnalysis> findByNovelId(@Param("novelId") Long novelId);
    
    @Select("SELECT * FROM chapter_analysis WHERE novel_id = #{novelId} AND analysis_type = #{analysisType} " +
            "AND start_chapter = #{startChapter} AND end_chapter = #{endChapter} ORDER BY created_at DESC LIMIT 1")
    ChapterAnalysis findLatestByTypeAndRange(
        @Param("novelId") Long novelId,
        @Param("analysisType") String analysisType,
        @Param("startChapter") Integer startChapter,
        @Param("endChapter") Integer endChapter
    );
    
    @Update("UPDATE chapter_analysis SET analysis_content = #{analysisContent}, word_count = #{wordCount} WHERE id = #{id}")
    int update(ChapterAnalysis analysis);
    
    @Delete("DELETE FROM chapter_analysis WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
    
    @Delete("DELETE FROM chapter_analysis WHERE novel_id = #{novelId}")
    int deleteByNovelId(@Param("novelId") Long novelId);
}

