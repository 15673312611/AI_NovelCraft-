package com.novel.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.domain.entity.NovelCharacterProfile;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface NovelCharacterProfileRepository extends BaseMapper<NovelCharacterProfile> {
    @Select("SELECT * FROM novel_character_profiles WHERE novel_id = #{novelId} ORDER BY importance_score DESC, last_appearance DESC")
    List<NovelCharacterProfile> findByNovelId(@Param("novelId") Long novelId);

    @Select("SELECT * FROM novel_character_profiles WHERE novel_id = #{novelId} AND status = #{status}")
    List<NovelCharacterProfile> findByNovelIdAndStatus(@Param("novelId") Long novelId, @Param("status") String status);

    @Select("SELECT * FROM novel_character_profiles WHERE novel_id = #{novelId} AND name = #{name}")
    NovelCharacterProfile findByNovelIdAndName(@Param("novelId") Long novelId, @Param("name") String name);
}

