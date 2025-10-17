package com.novel.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.domain.entity.NovelChronicle;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface NovelChronicleRepository extends BaseMapper<NovelChronicle> {
    @Select("SELECT * FROM novel_chronicle WHERE novel_id = #{novelId} ORDER BY chapter_number ASC")
    List<NovelChronicle> findByNovelId(@Param("novelId") Long novelId);

    @Select("SELECT * FROM novel_chronicle WHERE novel_id = #{novelId} AND chapter_number BETWEEN #{startChapter} AND #{endChapter} ORDER BY chapter_number ASC")
    List<NovelChronicle> findByNovelIdAndChapterRange(@Param("novelId") Long novelId, @Param("startChapter") Integer startChapter, @Param("endChapter") Integer endChapter);

    @Select("SELECT * FROM novel_chronicle WHERE novel_id = #{novelId} AND event_type = #{eventType} ORDER BY chapter_number ASC")
    List<NovelChronicle> findByNovelIdAndEventType(@Param("novelId") Long novelId, @Param("eventType") String eventType);
}

