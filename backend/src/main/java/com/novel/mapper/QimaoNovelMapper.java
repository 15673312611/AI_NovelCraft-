package com.novel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.entity.QimaoNovel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 七猫小说Mapper接口
 */
@Mapper
public interface QimaoNovelMapper extends BaseMapper<QimaoNovel> {
    
    /**
     * 统计各分类小说数量
     */
    @Select("SELECT category, COUNT(*) as count FROM qimao_novels GROUP BY category ORDER BY count DESC")
    List<Map<String, Object>> countByCategory();
    
    /**
     * 统计各状态小说数量
     */
    @Select("SELECT status, COUNT(*) as count FROM qimao_novels GROUP BY status")
    List<Map<String, Object>> countByStatus();
    
    /**
     * 统计各作者小说数量（Top 20）
     */
    @Select("SELECT author, COUNT(*) as count FROM qimao_novels GROUP BY author ORDER BY count DESC LIMIT 20")
    List<Map<String, Object>> countByAuthor();
    
    /**
     * 获取最近更新的小说
     */
    @Select("SELECT * FROM qimao_novels ORDER BY created_at DESC LIMIT #{limit}")
    List<QimaoNovel> getRecentNovels(int limit);
    
    /**
     * 按排行榜类型统计
     */
    @Select("SELECT rank_type, COUNT(*) as count FROM qimao_novels WHERE rank_type IS NOT NULL GROUP BY rank_type")
    List<Map<String, Object>> countByRankType();
}
