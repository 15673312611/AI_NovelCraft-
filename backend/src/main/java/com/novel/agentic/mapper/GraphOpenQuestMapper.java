package com.novel.agentic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.agentic.entity.graph.GraphOpenQuest;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 开放任务Mapper
 */
@Mapper
public interface GraphOpenQuestMapper extends BaseMapper<GraphOpenQuest> {
    
    /**
     * 根据小说ID和任务ID查询
     */
    @Select("SELECT * FROM graph_open_quest WHERE novel_id = #{novelId} AND quest_id = #{questId}")
    GraphOpenQuest findByNovelIdAndQuestId(@Param("novelId") Long novelId, @Param("questId") String questId);
    
    /**
     * 查询小说的开放任务（未完成的）
     */
    @Select("SELECT * FROM graph_open_quest WHERE novel_id = #{novelId} AND status = 'OPEN' AND (due_by_chapter IS NULL OR due_by_chapter >= #{currentChapter}) ORDER BY due_by_chapter ASC, last_updated_chapter DESC LIMIT 10")
    List<GraphOpenQuest> findOpenQuests(@Param("novelId") Long novelId, @Param("currentChapter") Integer currentChapter);
    
    /**
     * 查询小说的所有任务
     */
    @Select("SELECT * FROM graph_open_quest WHERE novel_id = #{novelId} ORDER BY last_updated_chapter DESC")
    List<GraphOpenQuest> findByNovelId(@Param("novelId") Long novelId);
    
    /**
     * 删除小说的所有任务
     */
    @Delete("DELETE FROM graph_open_quest WHERE novel_id = #{novelId}")
    int deleteByNovelId(@Param("novelId") Long novelId);
    
    /**
     * 删除指定任务
     */
    @Delete("DELETE FROM graph_open_quest WHERE novel_id = #{novelId} AND quest_id = #{questId}")
    int deleteByNovelIdAndQuestId(@Param("novelId") Long novelId, @Param("questId") String questId);
    
    /**
     * 删除指定章节更新的任务
     */
    @Delete("DELETE FROM graph_open_quest WHERE novel_id = #{novelId} AND last_updated_chapter = #{chapterNumber}")
    int deleteByNovelIdAndChapter(@Param("novelId") Long novelId, @Param("chapterNumber") Integer chapterNumber);
    
    /**
     * 删除指定章节引入的任务
     */
    @Delete("DELETE FROM graph_open_quest WHERE novel_id = #{novelId} AND introduced_chapter = #{chapterNumber}")
    int deleteByIntroducedChapter(@Param("novelId") Long novelId, @Param("chapterNumber") Integer chapterNumber);
}
