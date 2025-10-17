package com.novel.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.domain.entity.ChapterSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Optional;

@Mapper
public interface ChapterSummaryRepository extends BaseMapper<ChapterSummary> {
    Optional<ChapterSummary> findByNovelIdAndChapterNumber(@Param("novelId") Long novelId, @Param("chapterNumber") Integer chapterNumber);
    List<ChapterSummary> findByNovelIdAndChapterNumberBetween(@Param("novelId") Long novelId, @Param("startChapter") Integer startChapter, @Param("endChapter") Integer endChapter);
    List<ChapterSummary> findByNovelIdOrderByChapterNumber(@Param("novelId") Long novelId);
    Integer getMaxChapterNumber(@Param("novelId") Long novelId);
    void deleteByNovelId(@Param("novelId") Long novelId);
}

