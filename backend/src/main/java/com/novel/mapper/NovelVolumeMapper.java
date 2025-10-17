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
     * 更新卷的实际字数
     */
    @Update("UPDATE novel_volumes SET actual_word_count = #{actualWordCount}, updated_at = NOW() WHERE id = #{volumeId}")
    int updateActualWordCount(@Param("volumeId") Long volumeId, @Param("actualWordCount") Integer actualWordCount);

    /**
     * 更新卷状态
     */
    @Update("UPDATE novel_volumes SET status = #{status}, updated_at = NOW() WHERE id = #{volumeId}")
    int updateStatus(@Param("volumeId") Long volumeId, @Param("status") String status);

    /**
     * 获取小说的卷统计信息
     */
    @Select("SELECT COUNT(*) as total_volumes, " +
            "SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed_volumes, " +
            "SUM(estimated_word_count) as total_estimated_words, " +
            "SUM(actual_word_count) as total_actual_words " +
            "FROM novel_volumes WHERE novel_id = #{novelId}")
    NovelVolumeStats getVolumeStats(@Param("novelId") Long novelId);

    /**
     * 卷统计信息内部类
     */
    class NovelVolumeStats {
        private Integer totalVolumes;
        private Integer completedVolumes;
        private Integer totalEstimatedWords;
        private Integer totalActualWords;

        public Integer getTotalVolumes() {
            return totalVolumes;
        }

        public void setTotalVolumes(Integer totalVolumes) {
            this.totalVolumes = totalVolumes;
        }

        public Integer getCompletedVolumes() {
            return completedVolumes;
        }

        public void setCompletedVolumes(Integer completedVolumes) {
            this.completedVolumes = completedVolumes;
        }

        public Integer getTotalEstimatedWords() {
            return totalEstimatedWords;
        }

        public void setTotalEstimatedWords(Integer totalEstimatedWords) {
            this.totalEstimatedWords = totalEstimatedWords;
        }

        public Integer getTotalActualWords() {
            return totalActualWords;
        }

        public void setTotalActualWords(Integer totalActualWords) {
            this.totalActualWords = totalActualWords;
        }

        public double getCompletionRate() {
            if (totalVolumes == null || totalVolumes == 0) {
                return 0.0;
            }
            return (completedVolumes * 100.0) / totalVolumes;
        }

        public double getWordProgressRate() {
            if (totalEstimatedWords == null || totalEstimatedWords == 0) {
                return 0.0;
            }
            return Math.min(100.0, (totalActualWords * 100.0) / totalEstimatedWords);
        }
    }
}