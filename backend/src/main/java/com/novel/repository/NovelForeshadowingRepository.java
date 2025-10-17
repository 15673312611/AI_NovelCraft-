package com.novel.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.domain.entity.NovelForeshadowing;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface NovelForeshadowingRepository extends BaseMapper<NovelForeshadowing> {
    @Select("SELECT * FROM novel_foreshadowing WHERE novel_id = #{novelId} ORDER BY priority DESC, planted_chapter ASC")
    List<NovelForeshadowing> findByNovelId(@Param("novelId") Long novelId);

    @Select("SELECT * FROM novel_foreshadowing WHERE novel_id = #{novelId} AND status = #{status} ORDER BY priority DESC, planted_chapter ASC")
    List<NovelForeshadowing> findByNovelIdAndStatus(@Param("novelId") Long novelId, @Param("status") String status);

    @Select("SELECT * FROM novel_foreshadowing WHERE novel_id = #{novelId} AND type = #{type} ORDER BY priority DESC, planted_chapter ASC")
    List<NovelForeshadowing> findByNovelIdAndType(@Param("novelId") Long novelId, @Param("type") String type);
}

