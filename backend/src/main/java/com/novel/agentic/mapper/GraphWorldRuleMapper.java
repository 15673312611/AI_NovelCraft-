package com.novel.agentic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.agentic.entity.graph.GraphWorldRule;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 世界规则Mapper
 */
@Mapper
public interface GraphWorldRuleMapper extends BaseMapper<GraphWorldRule> {
    
    /**
     * 查询适用的世界规则
     */
    @Select("SELECT * FROM graph_world_rule WHERE novel_id = #{novelId} AND (scope = 'global' OR applicable_chapter IS NULL OR applicable_chapter <= #{chapterNumber}) ORDER BY CASE category WHEN 'power_system' THEN 10 WHEN 'world_setting' THEN 8 WHEN 'character_constraint' THEN 6 ELSE 5 END DESC, importance DESC LIMIT #{limit}")
    List<GraphWorldRule> findApplicableRules(@Param("novelId") Long novelId, @Param("chapterNumber") Integer chapterNumber, @Param("limit") Integer limit);
    
    /**
     * 查询小说的所有世界规则
     */
    @Select("SELECT * FROM graph_world_rule WHERE novel_id = #{novelId} ORDER BY importance DESC")
    List<GraphWorldRule> findByNovelId(@Param("novelId") Long novelId);
    
    /**
     * 根据小说ID和规则ID查询
     */
    @Select("SELECT * FROM graph_world_rule WHERE novel_id = #{novelId} AND rule_id = #{ruleId}")
    GraphWorldRule findByNovelIdAndRuleId(@Param("novelId") Long novelId, @Param("ruleId") String ruleId);
    
    /**
     * 删除小说的所有世界规则
     */
    @Delete("DELETE FROM graph_world_rule WHERE novel_id = #{novelId}")
    int deleteByNovelId(@Param("novelId") Long novelId);
}
