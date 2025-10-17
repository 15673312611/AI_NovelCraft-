package com.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 大事年表实体类
 */
@Data
@TableName("novel_chronicle")
public class NovelChronicle {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("novel_id")
    private Long novelId;

    @TableField("chapter_number")
    private Integer chapterNumber;

    private String events; // JSON格式

    @TableField("timeline_info")
    private String timelineInfo;

    @TableField("event_type")
    private String eventType;

    @TableField("importance_level")
    private Integer importanceLevel;

    @TableField("created_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    // 事件类型枚举
    public enum EventType {
        DEATH, MARRIAGE, BREAKTHROUGH, BATTLE, DISCOVERY, TRAVEL, DECISION
    }
} 