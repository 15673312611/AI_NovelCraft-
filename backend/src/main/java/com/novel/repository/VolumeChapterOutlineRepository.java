package com.novel.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.domain.entity.VolumeChapterOutline;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;


import java.util.List;

/**
 * 卷级章纲 Repository
 */
@Mapper
public interface VolumeChapterOutlineRepository extends BaseMapper<VolumeChapterOutline> {

    /**
     * 根据小说ID查询所有章纲（按全局章节号排序）
     */
    @Select("SELECT * FROM volume_chapter_outlines WHERE novel_id = #{novelId} ORDER BY global_chapter_number ASC")
    List<VolumeChapterOutline> findByNovelId(@Param("novelId") Long novelId);

    /**
     * 根据卷ID查询所有章纲（按卷内章节号排序）
     */
    @Select("SELECT * FROM volume_chapter_outlines WHERE volume_id = #{volumeId} ORDER BY chapter_in_volume ASC")
    List<VolumeChapterOutline> findByVolumeId(@Param("volumeId") Long volumeId);

    /**
     * 根据小说ID和全局章节号查询章纲
     */
    @Select("SELECT * FROM volume_chapter_outlines WHERE novel_id = #{novelId} AND global_chapter_number = #{globalChapterNumber} LIMIT 1")
    VolumeChapterOutline findByNovelAndGlobalChapter(@Param("novelId") Long novelId, @Param("globalChapterNumber") Integer globalChapterNumber);

    /**
     * 根据卷ID和卷内章节号查询章纲
     */
    @Select("SELECT * FROM volume_chapter_outlines WHERE volume_id = #{volumeId} AND chapter_in_volume = #{chapterInVolume} LIMIT 1")
    VolumeChapterOutline findByVolumeAndChapter(@Param("volumeId") Long volumeId, @Param("chapterInVolume") Integer chapterInVolume);

    /**
     * 查询某卷的所有ACTIVE伏笔（用于下一卷生成时的上下文）
     */
    @Select("SELECT * FROM volume_chapter_outlines WHERE volume_id = #{volumeId} AND foreshadow_action IN ('PLANT', 'DEEPEN', 'REFERENCE') ORDER BY chapter_in_volume ASC")
    List<VolumeChapterOutline> findActiveForeshadowsByVolume(@Param("volumeId") Long volumeId);

    /**
     * 删除某卷的所有章纲（覆盖式重生成时使用）
     */
    @Delete("DELETE FROM volume_chapter_outlines WHERE volume_id = #{volumeId}")
    int deleteByVolumeId(@Param("volumeId") Long volumeId);
}

