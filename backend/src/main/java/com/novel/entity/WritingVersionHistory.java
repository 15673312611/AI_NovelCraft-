package com.novel.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 写作内容版本历史实体
 * 目前主要用于章节正文的历史版本记录
 */
@Data
public class WritingVersionHistory {

    /**
     * 版本记录ID
     */
    private Long id;

    /**
     * 小说ID
     */
    private Long novelId;

    /**
     * 章节ID（正文场景使用）
     */
    private Long chapterId;

    /**
     * 文档ID（辅助文档，如设定/角色等，可选）
     */
    private Long documentId;

    /**
     * 版本来源：
     * AUTO_SAVE / MANUAL_SAVE / AI_REPLACE 等
     */
    private String sourceType;

    /**
     * 内容快照
     */
    private String content;

    /**
     * 字数统计（不含空白）
     */
    private Integer wordCount;

    /**
     * 相对于上一版本的差异百分比（0-100）
     */
    private Double diffRatio;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}

