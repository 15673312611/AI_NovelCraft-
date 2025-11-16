package com.novel.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.domain.entity.ForeshadowLifecycleLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 伏笔生命周期日志 Repository
 */
@Mapper
public interface ForeshadowLifecycleLogRepository extends BaseMapper<ForeshadowLifecycleLog> {

    /**
     * 根据伏笔ID查询所有生命周期记录（按时间排序）
     */
    @Select("SELECT * FROM foreshadow_lifecycle_log WHERE foreshadow_id = #{foreshadowId} ORDER BY decided_at ASC")
    List<ForeshadowLifecycleLog> findByForeshadowId(@Param("foreshadowId") Long foreshadowId);

    /**
     * 根据小说ID和卷号查询该卷的所有伏笔动作
     */
    @Select("SELECT * FROM foreshadow_lifecycle_log WHERE novel_id = #{novelId} AND volume_number = #{volumeNumber} ORDER BY chapter_in_volume ASC, decided_at ASC")
    List<ForeshadowLifecycleLog> findByNovelAndVolume(@Param("novelId") Long novelId, @Param("volumeNumber") Integer volumeNumber);

    /**
     * 查询某小说前N卷的所有ACTIVE伏笔（未RESOLVE的）
     */
    @Select("SELECT DISTINCT foreshadow_id FROM foreshadow_lifecycle_log " +
            "WHERE novel_id = #{novelId} AND volume_number < #{beforeVolume} AND action IN ('PLANT', 'DEEPEN', 'REFERENCE') " +
            "AND foreshadow_id NOT IN (" +
            "  SELECT foreshadow_id FROM foreshadow_lifecycle_log WHERE novel_id = #{novelId} AND volume_number < #{beforeVolume} AND action = 'RESOLVE'" +
            ")")
    List<Long> findActiveForeshadowIds(@Param("novelId") Long novelId, @Param("beforeVolume") Integer beforeVolume);

    /**
     * 删除某卷的所有伏笔生命周期日志（覆盖式重生成时使用）
     */
    @Delete("DELETE FROM foreshadow_lifecycle_log WHERE volume_id = #{volumeId}")
    int deleteByVolumeId(@Param("volumeId") Long volumeId);
}

