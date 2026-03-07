package com.novel.agentic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.agentic.entity.graph.GraphCharacterState;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 角色状态Mapper
 */
@Mapper
public interface GraphCharacterStateMapper extends BaseMapper<GraphCharacterState> {
    
    /**
     * 根据小说ID和角色名查询
     */
    @Select("SELECT * FROM graph_character_state WHERE novel_id = #{novelId} AND character_name = #{characterName}")
    GraphCharacterState findByNovelIdAndCharacterName(@Param("novelId") Long novelId, @Param("characterName") String characterName);
    
    /**
     * 查询小说的所有角色状态
     */
    @Select("SELECT * FROM graph_character_state WHERE novel_id = #{novelId} ORDER BY last_updated_chapter DESC LIMIT #{limit}")
    List<GraphCharacterState> findByNovelIdWithLimit(@Param("novelId") Long novelId, @Param("limit") Integer limit);
    
    /**
     * 删除小说的所有角色状态
     */
    @Delete("DELETE FROM graph_character_state WHERE novel_id = #{novelId}")
    int deleteByNovelId(@Param("novelId") Long novelId);
    
    /**
     * 删除指定章节更新的角色状态
     */
    @Delete("DELETE FROM graph_character_state WHERE novel_id = #{novelId} AND last_updated_chapter = #{chapterNumber}")
    int deleteByNovelIdAndChapter(@Param("novelId") Long novelId, @Param("chapterNumber") Integer chapterNumber);
    
    /**
     * 查询指定章节更新的角色状态
     */
    @Select("SELECT * FROM graph_character_state WHERE novel_id = #{novelId} AND last_updated_chapter = #{chapterNumber}")
    List<GraphCharacterState> findByNovelIdAndChapter(@Param("novelId") Long novelId, @Param("chapterNumber") Integer chapterNumber);
    
    /**
     * 获取最大更新章节
     */
    @Select("SELECT MAX(last_updated_chapter) FROM graph_character_state WHERE novel_id = #{novelId}")
    Integer getMaxUpdatedChapter(@Param("novelId") Long novelId);
}
