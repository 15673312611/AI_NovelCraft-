package com.novel.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.domain.entity.Character;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface CharacterRepository extends BaseMapper<Character> {
    @Select("SELECT * FROM characters WHERE novel_id = #{novelId} ORDER BY CASE WHEN character_type = 'PROTAGONIST' THEN 1 WHEN character_type = 'MAJOR' THEN 2 WHEN character_type = 'MINOR' THEN 3 ELSE 4 END, created_at ASC")
    List<Character> findByNovelId(@Param("novelId") Long novelId);

    @Select("SELECT * FROM characters WHERE novel_id = #{novelId}")
    IPage<Character> findByNovelId(@Param("novelId") Long novelId, Page<Character> page);

    @Select("SELECT * FROM characters WHERE novel_id = #{novelId} AND is_protagonist = #{isProtagonist}")
    List<Character> findByNovelIdAndIsProtagonist(@Param("novelId") Long novelId, @Param("isProtagonist") Boolean isProtagonist);

    @Select("SELECT * FROM characters WHERE novel_id = #{novelId} AND is_antagonist = #{isAntagonist}")
    List<Character> findByNovelIdAndIsAntagonist(@Param("novelId") Long novelId, @Param("isAntagonist") Boolean isAntagonist);

    @Select("SELECT * FROM characters WHERE novel_id = #{novelId} AND is_major_character = #{isMajorCharacter}")
    List<Character> findByNovelIdAndIsMajorCharacter(@Param("novelId") Long novelId, @Param("isMajorCharacter") Boolean isMajorCharacter);

    @Select("SELECT * FROM characters WHERE novel_id = #{novelId} AND (LOWER(name) LIKE LOWER(CONCAT('%', #{query}, '%')) OR LOWER(description) LIKE LOWER(CONCAT('%', #{query}, '%')) OR LOWER(background) LIKE LOWER(CONCAT('%', #{query}, '%')))")
    List<Character> searchByName(@Param("novelId") Long novelId, @Param("query") String query);

    @Select("SELECT COUNT(*) FROM characters WHERE novel_id = #{novelId}")
    long countByNovelId(@Param("novelId") Long novelId);

    @Select("SELECT COUNT(*) FROM characters WHERE novel_id = #{novelId} AND is_major_character = true")
    long countMajorCharactersByNovelId(@Param("novelId") Long novelId);

    @Select("SELECT * FROM characters WHERE novel_id = #{novelId} ORDER BY updated_at DESC, created_at DESC")
    List<Character> findActiveCharactersByNovelId(@Param("novelId") Long novelId);

    @Select("SELECT * FROM characters WHERE novel_id = #{novelId} ORDER BY created_at DESC")
    List<Character> findInactiveCharacters(@Param("novelId") Long novelId, @Param("currentChapter") Integer currentChapter, @Param("threshold") Integer threshold);

    @Select("SELECT * FROM characters WHERE novel_id = #{novelId} AND name = #{name}")
    Character findByNovelIdAndName(@Param("novelId") Long novelId, @Param("name") String name);
}

