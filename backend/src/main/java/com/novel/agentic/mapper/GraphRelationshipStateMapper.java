package com.novel.agentic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.agentic.entity.graph.GraphRelationshipState;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 关系状态Mapper
 */
@Mapper
public interface GraphRelationshipStateMapper extends BaseMapper<GraphRelationshipState> {
    
    /**
     * 根据小说ID和角色对查询
     */
    @Select("SELECT * FROM graph_relationship_state WHERE novel_id = #{novelId} AND character_a = #{charA} AND character_b = #{charB}")
    GraphRelationshipState findByNovelIdAndCharacters(@Param("novelId") Long novelId, @Param("charA") String charA, @Param("charB") String charB);
    
    /**
     * 查询小说的所有关系状态（按强度排序）
     */
    @Select("SELECT * FROM graph_relationship_state WHERE novel_id = #{novelId} ORDER BY strength DESC, last_updated_chapter DESC LIMIT #{limit}")
    List<GraphRelationshipState> findByNovelIdWithLimit(@Param("novelId") Long novelId, @Param("limit") Integer limit);
    
    /**
     * 查询角色的所有关系
     */
    @Select("SELECT * FROM graph_relationship_state WHERE novel_id = #{novelId} AND (character_a = #{characterName} OR character_b = #{characterName}) ORDER BY strength DESC LIMIT #{limit}")
    List<GraphRelationshipState> findByCharacterName(@Param("novelId") Long novelId, @Param("characterName") String characterName, @Param("limit") Integer limit);
    
    /**
     * 删除小说的所有关系状态
     */
    @Delete("DELETE FROM graph_relationship_state WHERE novel_id = #{novelId}")
    int deleteByNovelId(@Param("novelId") Long novelId);
    
    /**
     * 删除指定章节更新的关系状态
     */
    @Delete("DELETE FROM graph_relationship_state WHERE novel_id = #{novelId} AND last_updated_chapter = #{chapterNumber}")
    int deleteByNovelIdAndChapter(@Param("novelId") Long novelId, @Param("chapterNumber") Integer chapterNumber);
    
    /**
     * 查询指定章节更新的关系状态
     */
    @Select("SELECT * FROM graph_relationship_state WHERE novel_id = #{novelId} AND last_updated_chapter = #{chapterNumber}")
    List<GraphRelationshipState> findByNovelIdAndChapter(@Param("novelId") Long novelId, @Param("chapterNumber") Integer chapterNumber);
    
    /**
     * 删除指定关系
     */
    @Delete("DELETE FROM graph_relationship_state WHERE novel_id = #{novelId} AND character_a = #{charA} AND character_b = #{charB}")
    int deleteByNovelIdAndCharacters(@Param("novelId") Long novelId, @Param("charA") String charA, @Param("charB") String charB);
}
