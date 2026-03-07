package com.novel.shortstory.entity;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 短篇小说实体
 * 支持AI自动化工作流生成
 */
@Entity
@Table(name = "short_novels")
@Data
public class ShortNovel {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 所属用户ID
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    /**
     * 小说标题
     */
    @Column(nullable = false, length = 200)
    private String title;
    
    /**
     * 创作构思
     */
    @Column(columnDefinition = "TEXT")
    private String idea;
    
    /**
     * 故事设定（世界观/角色/基调等，工作流产物）
     */
    @Column(name = "story_setting", columnDefinition = "TEXT")
    private String storySetting;
    
    /**
     * AI生成的完整大纲
     */
    @Column(columnDefinition = "TEXT")
    private String outline;

    /**
     * 章节看点（标题+核心）完整JSON（用于工作流画布/回溯）
     */
    @Column(name = "hooks_json", columnDefinition = "TEXT")
    private String hooksJson;
    
    /**
     * 导语（黄金开头）- 第一章的开篇内容
     */
    @Column(columnDefinition = "TEXT")
    private String prologue;

    /**
     * 当前正在执行的工作流步骤（用于前端画布高亮）
     */
    @Column(name = "active_step", length = 100)
    private String activeStep;
    
    /**
     * 目标总字数（1-5万）
     */
    @Column(name = "target_words")
    private Integer targetWords = 30000;
    
    /**
     * 目标章节数（3-15章）
     */
    @Column(name = "chapter_count")
    private Integer chapterCount = 10;
    
    /**
     * 每章目标字数
     */
    @Column(name = "words_per_chapter")
    private Integer wordsPerChapter = 3000;
    
    /**
     * 工作流状态
     * DRAFT - 草稿
     * GENERATING_OUTLINE - 生成大纲中
     * OUTLINE_READY - 大纲已生成
     * WORKFLOW_RUNNING - 工作流运行中
     * WORKFLOW_PAUSED - 工作流暂停
     * COMPLETED - 完成
     * FAILED - 失败
     */
    @Column(nullable = false, length = 50)
    private String status = "DRAFT";
    
    /**
     * 当前处理到的章节号（从1开始）
     */
    @Column(name = "current_chapter")
    private Integer currentChapter = 0;
    
    /**
     * 当前章节的重试次数
     */
    @Column(name = "current_retry_count")
    private Integer currentRetryCount = 0;
    
    /**
     * 最大重试次数（每章）
     */
    @Column(name = "max_retry_per_chapter")
    private Integer maxRetryPerChapter = 3;
    
    /**
     * 审稿通过的最低分数（1-10分）
     */
    @Column(name = "min_pass_score")
    private Integer minPassScore = 7;
    
    /**
     * 是否启用动态大纲更新
     */
    @Column(name = "enable_outline_update")
    private Boolean enableOutlineUpdate = true;
    
    /**
     * 工作流配置（JSON格式）
     * 包含：温度、模型、审稿标准等
     */
    @Column(columnDefinition = "TEXT")
    private String workflowConfig;
    
    /**
     * 错误信息（工作流失败时）
     */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    /**
     * 总字数统计
     */
    @Column(name = "total_words")
    private Integer totalWords = 0;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
