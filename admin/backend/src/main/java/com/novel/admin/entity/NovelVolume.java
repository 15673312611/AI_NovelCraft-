package com.novel.admin.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 小说分卷实体类
 * 对应 novel_volumes 表
 */
@Data
public class NovelVolume {
    private Long id;
    private Long novelId;
    private Long outlineId;
    private String title;
    private String theme;
    private String description;
    private String contentOutline;
    private Integer volumeNumber;
    private Integer chapterStart;
    private Integer chapterEnd;
    private Integer estimatedWordCount;
    private Integer actualWordCount;
    private String keyEvents;
    private String characterDevelopment;
    private String plotThreads;
    private String status; // PLANNED, IN_PROGRESS, COMPLETED, REVISED
    private Boolean isAiGenerated;
    private String lastModifiedByAi;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
