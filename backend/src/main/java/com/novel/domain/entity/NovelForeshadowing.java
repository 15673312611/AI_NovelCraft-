package com.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 伏笔追踪实体类
 */
@Data
@TableName("novel_foreshadowing")
public class NovelForeshadowing {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("novel_id")
    private Long novelId;

    private String content;

    @TableField("planted_chapter")
    private Integer plantedChapter;

    @TableField("resolved_chapter")
    private Integer resolvedChapter;

    private String status;

    private String type;

    private Integer priority;

    @TableField("context_info")
    private String contextInfo;

    @TableField("created_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    @TableField("resolved_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime resolvedTime;

    // 伏笔状态枚举
    public enum ForeshadowingStatus {
        ACTIVE, RESOLVED, ABANDONED
    }

    // 伏笔类型枚举
    public enum ForeshadowingType {
        DEATH, ROMANCE, CONFLICT, MYSTERY, POWER, OTHER
    }
} 