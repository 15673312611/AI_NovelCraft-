package com.novel.admin.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 卷级章节大纲实体类
 * 对应 volume_chapter_outlines 表
 */
@Data
public class VolumeChapterOutline {
    private Long id;
    private Long novelId;
    private Long volumeId;
    private Integer volumeNumber;
    private Integer chapterInVolume;
    private Integer globalChapterNumber;
    private String direction;
    private String keyPlotPoints; // JSON 字符串
    private String emotionalTone;
    private String foreshadowAction; // NONE, PLANT, REFERENCE, DEEPEN, RESOLVE
    private String foreshadowDetail; // JSON 字符串
    private String subplot;
    private String antagonism; // JSON 字符串
    private String status; // PENDING, WRITTEN, REVISED
    private String reactDecisionLog;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
