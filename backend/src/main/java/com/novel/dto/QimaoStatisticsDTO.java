package com.novel.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 七猫小说统计数据DTO
 */
@Data
public class QimaoStatisticsDTO {
    /**
     * 总小说数
     */
    private Long totalNovels;
    
    /**
     * 分类统计
     */
    private List<Map<String, Object>> categoryStats;
    
    /**
     * 状态统计
     */
    private List<Map<String, Object>> statusStats;
    
    /**
     * 作者统计（Top 20）
     */
    private List<Map<String, Object>> authorStats;
    
    /**
     * 排行榜类型统计
     */
    private List<Map<String, Object>> rankTypeStats;
    
    /**
     * 最近爬取的小说
     */
    private List<QimaoNovelDTO> recentNovels;
    
    /**
     * 任务统计
     */
    private Map<String, Object> taskStats;
}
