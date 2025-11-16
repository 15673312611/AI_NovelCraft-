package com.novel.agentic.model;

import lombok.Data;
import lombok.Builder;
import java.util.Map;

/**
 * 图数据库实体
 */
@Data
@Builder
public class GraphEntity {
    
    /**
     * 实体类型（Event/Character/Location/Foreshadow/Plotline/WorldRule）
     */
    private String type;
    
    /**
     * 实体ID
     */
    private String id;
    
    /**
     * 实体属性
     */
    private Map<String, Object> properties;
    
    /**
     * 关联章节
     */
    private Integer chapterNumber;
    
    /**
     * 相关性分数（用于排序）
     */
    private Double relevanceScore;
    
    /**
     * 来源说明
     */
    private String source;
}


