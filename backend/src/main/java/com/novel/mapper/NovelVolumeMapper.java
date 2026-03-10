package com.novel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.domain.entity.NovelVolume;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 小说卷数据访问层
 */
@Mapper
public interface NovelVolumeMapper extends BaseMapper<NovelVolume> {

    /**
     * 根据小说ID查询所有卷
     */
    @Select("SELECT * FROM novel_volumes WHERE novel_id = #{novelId} ORDER BY volume_number ASC")
    List<NovelVolume> selectByNovelId(@Param("novelId") Long novelId);

    /**
     * 根据卷号查询卷
     */
    @Select("SELECT * FROM novel_volumes WHERE novel_id = #{novelId} AND volume_number = #{volumeNumber}")
    NovelVolume selectByVolumeNumber(@Param("novelId") Long novelId, @Param("volumeNumber") Integer volumeNumber);

    /**
     * 根据章节号查询所属的卷
     * 通过章节范围 (chapter_start <= chapterNumber <= chapter_end) 来查找
     */
    @Select("SELECT * FROM novel_volumes " +
            "WHERE novel_id = #{novelId} " +
            "AND chapter_start <= #{chapterNumber} " +
            "AND chapter_end >= #{chapterNumber} " +
            "LIMIT 1")
    NovelVolume selectByChapterNumber(@Param("novelId") Long novelId, @Param("chapterNumber") Integer chapterNumber);

    /**
     * 更新卷状态
     */
    @Update("UPDATE novel_volumes SET status = #{status}, updated_at = NOW() WHERE id = #{volumeId}")
    int updateStatus(@Param("volumeId") Long volumeId, @Param("status") String status);

}
