package com.novel.agentic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.agentic.entity.graph.GraphEvent;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 事件Mapper
 */
@Mapper
public interface GraphEventMapper extends BaseMapper<GraphEvent> {
    
    /**
     * 根据小说ID和事件ID查询
     */
    @Select("SELECT * FROM graph_event WHERE novel_id = #{novelId} AND event_id = #{eventId}")
    GraphEvent findByNovelIdAndEventId(@Param("novelId") Long novelId, @Param("eventId") String eventId);
    
    /**
     * 查询指定章节之前的相关事件（按重要性和章节排序）
     */
    @Select("SELECT * FROM graph_event WHERE novel_id = #{novelId} AND chapter_number < #{chapterNumber} ORDER BY importance DESC, chapter_number DESC LIMIT #{limit}")
    List<GraphEvent> findRelevantEvents(@Param("novelId") Long novelId, @Param("chapterNumber") Integer chapterNumber, @Param("limit") Integer limit);
    
    /**
     * 查询小说的所有事件
     */
    @Select("SELECT * FROM graph_event WHERE novel_id = #{novelId} ORDER BY chapter_number DESC")
    List<GraphEvent> findByNovelId(@Param("novelId") Long novelId);
    
    /**
     * 查询指定章节的事件
     */
    @Select("SELECT * FROM graph_event WHERE novel_id = #{novelId} AND chapter_number = #{chapterNumber}")
    List<GraphEvent> findByNovelIdAndChapter(@Param("novelId") Long novelId, @Param("chapterNumber") Integer chapterNumber);
    
    /**
     * 删除小说的所有事件
     */
    @Delete("DELETE FROM graph_event WHERE novel_id = #{novelId}")
    int deleteByNovelId(@Param("novelId") Long novelId);
    
    /**
     * 删除指定章节的事件
     */
    @Delete("DELETE FROM graph_event WHERE novel_id = #{novelId} AND chapter_number = #{chapterNumber}")
    int deleteByNovelIdAndChapter(@Param("novelId") Long novelId, @Param("chapterNumber") Integer chapterNumber);
}
