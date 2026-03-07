package com.novel.agentic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.agentic.entity.graph.GraphForeshadowing;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 伏笔Mapper
 */
@Mapper
public interface GraphForeshadowingMapper extends BaseMapper<GraphForeshadowing> {
    
    /**
     * 查询未回收的伏笔
     */
    @Select("SELECT * FROM graph_foreshadowing WHERE novel_id = #{novelId} AND introduced_chapter < #{chapterNumber} AND status != 'REVEALED' AND (planned_reveal_chapter IS NULL OR planned_reveal_chapter <= #{chapterNumber} + 10) ORDER BY CASE WHEN importance = 'high' THEN 3 WHEN importance = 'medium' THEN 2 ELSE 1 END DESC, (#{chapterNumber} - introduced_chapter) DESC LIMIT #{limit}")
    List<GraphForeshadowing> findUnresolvedForeshadows(@Param("novelId") Long novelId, @Param("chapterNumber") Integer chapterNumber, @Param("limit") Integer limit);
    
    /**
     * 查询小说的所有伏笔
     */
    @Select("SELECT * FROM graph_foreshadowing WHERE novel_id = #{novelId} ORDER BY introduced_chapter DESC")
    List<GraphForeshadowing> findByNovelId(@Param("novelId") Long novelId);
    
    /**
     * 根据小说ID和伏笔ID查询
     */
    @Select("SELECT * FROM graph_foreshadowing WHERE novel_id = #{novelId} AND foreshadow_id = #{foreshadowId}")
    GraphForeshadowing findByNovelIdAndForeshadowId(@Param("novelId") Long novelId, @Param("foreshadowId") String foreshadowId);
    
    /**
     * 删除小说的所有伏笔
     */
    @Delete("DELETE FROM graph_foreshadowing WHERE novel_id = #{novelId}")
    int deleteByNovelId(@Param("novelId") Long novelId);
    
    /**
     * 删除指定章节引入或解决的伏笔
     */
    @Delete("DELETE FROM graph_foreshadowing WHERE novel_id = #{novelId} AND (introduced_chapter = #{chapterNumber} OR resolved_chapter = #{chapterNumber})")
    int deleteByNovelIdAndChapter(@Param("novelId") Long novelId, @Param("chapterNumber") Integer chapterNumber);
}
