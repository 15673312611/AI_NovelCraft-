package com.novel.admin.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 章节实体类
 * 对应 chapters 表
 */
@Data
public class Chapter {
    private Long id;
    private String title;
    private String subtitle;
    private String content;
    private String simpleContent;
    private Integer orderNum;
    private String status; // DRAFT, IN_PROGRESS, WRITING, REVIEW, REVIEWING, PUBLISHED, COMPLETED, ARCHIVED
    private Integer wordCount;
    private Integer chapterNumber;
    private String summary;
    private String notes;
    private Boolean isPublic;
    private LocalDateTime publishedAt;
    private Integer readingTimeMinutes;
    private Long previousChapterId;
    private Long nextChapterId;
    private Long novelId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String generationContext;
    private String reactDecisionLog;
}
