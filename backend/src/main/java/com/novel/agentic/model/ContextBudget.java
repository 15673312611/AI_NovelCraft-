package com.novel.agentic.model;

import lombok.Data;
import lombok.Builder;

/**
 * 上下文配额控制（防止token溢出）
 */
@Data
@Builder
public class ContextBudget {
    
    /**
     * 最大事件数
     */
    @Builder.Default
    private Integer maxEvents = 8;
    
    /**
     * 最大伏笔数
     */
    @Builder.Default
    private Integer maxForeshadows = 6;
    
    /**
     * 最大情节线数
     */
    @Builder.Default
    private Integer maxPlotlines = 3;
    
    /**
     * 最大世界规则数
     */
    @Builder.Default
    private Integer maxWorldRules = 5;
    
    /**
     * 最近完整章节数
     */
    @Builder.Default
    private Integer maxFullChapters = 2;
    
    /**
     * 最近摘要章节数
     */
    @Builder.Default
    private Integer maxSummaryChapters = 20;
    
    /**
     * 总token预算
     */
    @Builder.Default
    private Integer totalTokenBudget = 100000;
}


