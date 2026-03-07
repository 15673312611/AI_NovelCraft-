package com.novel.admin.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 小说实体类
 * 对应 novels 表
 */
@Data
public class Novel {
    private Long id;
    private String title;
    private String subtitle;
    private String coverImageUrl;
    private String description;
    private String genre;
    private String tags;
    private String targetAudience;
    private LocalDateTime estimatedCompletion;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Boolean isPublic;
    private BigDecimal rating;
    private Integer ratingCount;
    private Integer targetTotalChapters;
    private Integer wordsPerChapter;
    private Integer plannedVolumeCount;
    private Integer totalWordTarget;
    private String status; // DRAFT, WRITING, REVIEWING, COMPLETED
    private Integer wordCount;
    private Integer chapterCount;
    private Long authorId;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String outline; // 整本书大纲
    private String creationStage; // 创作阶段状态
}
