package com.novel.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.domain.entity.NovelWorldDictionary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface NovelWorldDictionaryRepository extends BaseMapper<NovelWorldDictionary> {
    @Select("SELECT * FROM novel_world_dictionary WHERE novel_id = #{novelId} ORDER BY is_important DESC, usage_count DESC")
    List<NovelWorldDictionary> findByNovelId(@Param("novelId") Long novelId);
    
    @Select("SELECT * FROM novel_world_dictionary WHERE novel_id = #{novelId} AND type = #{type} ORDER BY is_important DESC, usage_count DESC")
    List<NovelWorldDictionary> findByNovelIdAndType(@Param("novelId") Long novelId, @Param("type") String type);
    
    @Select("SELECT * FROM novel_world_dictionary WHERE novel_id = #{novelId} AND is_important = #{isImportant} ORDER BY usage_count DESC")
    List<NovelWorldDictionary> findByNovelIdAndImportant(@Param("novelId") Long novelId, @Param("isImportant") Boolean isImportant);
    
    @Select("SELECT * FROM novel_world_dictionary WHERE novel_id = #{novelId} AND term = #{term}")
    NovelWorldDictionary findByNovelIdAndTerm(@Param("novelId") Long novelId, @Param("term") String term);
    
    @Select("SELECT * FROM novel_world_dictionary WHERE novel_id = #{novelId} AND first_mention = #{firstMention} ORDER BY is_important DESC")
    List<NovelWorldDictionary> findByNovelIdAndFirstMention(@Param("novelId") Long novelId, @Param("firstMention") Integer firstMention);
}

