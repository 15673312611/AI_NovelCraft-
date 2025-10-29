package com.novel.repository;

import com.novel.entity.NovelTemplateProgress;
import org.apache.ibatis.annotations.*;

/**
 * 小说模板进度数据访问层
 */
@Mapper
public interface NovelTemplateProgressRepository {
    
    /**
     * 根据小说ID查询进度
     */
    @Select("SELECT * FROM novel_template_progress WHERE novel_id = #{novelId}")
    NovelTemplateProgress findByNovelId(@Param("novelId") Long novelId);
    
    /**
     * 插入新进度记录
     */
    @Insert("INSERT INTO novel_template_progress " +
            "(novel_id, enabled, current_stage, loop_number, stage_start_chapter, " +
            "template_type, last_updated_chapter, start_chapter, created_at, updated_at) " +
            "VALUES (#{novelId}, #{enabled}, #{currentStage}, #{loopNumber}, #{stageStartChapter}, " +
            "#{templateType}, #{lastUpdatedChapter}, #{startChapter}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NovelTemplateProgress progress);
    
    /**
     * 更新进度
     */
    @Update("UPDATE novel_template_progress SET " +
            "enabled = #{enabled}, " +
            "current_stage = #{currentStage}, " +
            "loop_number = #{loopNumber}, " +
            "stage_start_chapter = #{stageStartChapter}, " +
            "motivation_analysis = #{motivationAnalysis}, " +
            "bonus_analysis = #{bonusAnalysis}, " +
            "confrontation_analysis = #{confrontationAnalysis}, " +
            "response_analysis = #{responseAnalysis}, " +
            "earning_analysis = #{earningAnalysis}, " +
            "template_type = #{templateType}, " +
            "last_updated_chapter = #{lastUpdatedChapter}, " +
            "start_chapter = #{startChapter}, " +
            "updated_at = NOW() " +
            "WHERE id = #{id}")
    int update(NovelTemplateProgress progress);
    
    /**
     * 删除进度记录
     */
    @Delete("DELETE FROM novel_template_progress WHERE novel_id = #{novelId}")
    int deleteByNovelId(@Param("novelId") Long novelId);
    
    /**
     * 切换启用状态
     */
    @Update("UPDATE novel_template_progress SET enabled = #{enabled}, updated_at = NOW() " +
            "WHERE novel_id = #{novelId}")
    int updateEnabled(@Param("novelId") Long novelId, @Param("enabled") Boolean enabled);
}

