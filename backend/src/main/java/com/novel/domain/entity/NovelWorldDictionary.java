package com.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 世界观词典实体类
 */
@Data
@TableName("novel_world_dictionary")
public class NovelWorldDictionary {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("novel_id")
    private Long novelId;

    private String term;

    private String type;

    @TableField("first_mention")
    private Integer firstMention;

    private String description;

    @TableField("context_info")
    private String contextInfo;

    @TableField("usage_count")
    private Integer usageCount;

    @TableField("is_important")
    private Boolean isImportant;

    @TableField("created_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    @TableField("updated_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedTime;

    // 词条类型枚举
    public enum TermType {
        GEOGRAPHY, POWER_SYSTEM, ORGANIZATION, ITEM, CONCEPT
    }
} 