package com.novel.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 小说模板循环进度实体
 * 用于跟踪每个小说的模板循环状态，实现节奏控制
 */
@Data
public class NovelTemplateProgress {
    
    private Long id;
    
    /**
     * 关联的小说ID（唯一）
     */
    private Long novelId;
    
    /**
     * 是否启用模板引擎
     * true: 启用，false: 关闭（回退到原生成逻辑）
     */
    private Boolean enabled;
    
    /**
     * 当前所处阶段
     * @see TemplateStage
     */
    private String currentStage;
    
    /**
     * 当前循环次数（第几轮）
     */
    private Integer loopNumber;
    
    /**
     * 当前阶段的起始章节号
     */
    private Integer stageStartChapter;
    
    /**
     * 动机分析内容（AI分析结果）
     */
    private String motivationAnalysis;
    
    /**
     * 金手指分析内容
     */
    private String bonusAnalysis;
    
    /**
     * 装逼/冲突分析内容
     */
    private String confrontationAnalysis;
    
    /**
     * 反馈分析内容
     */
    private String responseAnalysis;
    
    /**
     * 收获分析内容
     */
    private String earningAnalysis;
    
    /**
     * 模板类型
     * @see TemplateType
     */
    private String templateType;
    
    /**
     * 最后更新的章节号
     */
    private Integer lastUpdatedChapter;
    
    /**
     * 模板引擎启动章节（从第几章开始应用模板）
     */
    private Integer startChapter;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

