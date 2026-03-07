package com.novel.agentic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.agentic.entity.graph.GraphPlotline;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 情节线Mapper
 */
@Mapper
public interface GraphPlotlineMapper extends BaseMapper<GraphPlotline> {
    
    /**
     * 查询需要推进的情节线（久未更新或事件较少的）
     */
    @Select("SELECT * FROM graph_plotline WHERE novel_id = #{novelId} AND (#{chapterNumber} - COALESCE(last_touched_chapter, 0)) > 5 ORDER BY priority DESC, (#{chapterNumber} - COALESCE(last_touched_chapter, 0)) DESC LIMIT #{limit}")
    List<GraphPlotline> findIdlePlotlines(@Param("novelId") Long novelId, @Param("chapterNumber") Integer chapterNumber, @Param("limit") Integer limit);
    
    /**
     * 查询小说的所有情节线
     */
    @Select("SELECT * FROM graph_plotline WHERE novel_id = #{novelId} ORDER BY priority DESC")
    List<GraphPlotline> findByNovelId(@Param("novelId") Long novelId);
    
    /**
     * 根据小说ID和情节线ID查询
     */
    @Select("SELECT * FROM graph_plotline WHERE novel_id = #{novelId} AND plotline_id = #{plotlineId}")
    GraphPlotline findByNovelIdAndPlotlineId(@Param("novelId") Long novelId, @Param("plotlineId") String plotlineId);
    
    /**
     * 删除小说的所有情节线
     */
    @Delete("DELETE FROM graph_plotline WHERE novel_id = #{novelId}")
    int deleteByNovelId(@Param("novelId") Long novelId);
}
