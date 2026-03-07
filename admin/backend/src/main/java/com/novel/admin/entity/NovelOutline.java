package com.novel.admin.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 小说大纲实体类
 * 对应 novel_outlines 表
 */
@Data
public class NovelOutline {
    private Long id;
    private Long novelId;
    private String title;
    private String genre;
    private String basicIdea;
    private String coreTheme;
    private String mainCharacters;
    private String plotStructure;
    private String coreSettings;
    private String worldSetting;
    private String keyElements; // JSON 字符串
    private String conflictTypes; // JSON 字符串
    private Integer targetWordCount;
    private Integer targetChapterCount;
    private String status; // DRAFT, CONFIRMED, REVISED, REVISING
    private Boolean isAiGenerated;
    private String lastModifiedByAi;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String feedbackHistory;
    private Integer revisionCount;
    private String reactDecisionLog;
}
