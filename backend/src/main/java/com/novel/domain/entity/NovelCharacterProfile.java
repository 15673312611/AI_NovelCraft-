package com.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色档案实体类
 */
@Data
@TableName("novel_character_profiles")
public class NovelCharacterProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("novel_id")
    private Long novelId;

    private String name;

    @TableField("first_appearance")
    private Integer firstAppearance;

    @TableField("last_appearance")
    private Integer lastAppearance;

    @TableField("appearance_count")
    private Integer appearanceCount;

    private String status;

    @TableField("status_change_chapter")
    private Integer statusChangeChapter;

    @TableField("personality_traits")
    private String personalityTraits; // JSON格式

    @TableField("key_events")
    private String keyEvents; // JSON格式

    private String relationships; // JSON格式

    @TableField("actions_history")
    private String actionsHistory; // JSON格式

    @TableField("importance_score")
    private Integer importanceScore;

    @TableField("created_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    @TableField("updated_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedTime;

    // 角色状态枚举
    public enum CharacterStatus {
        ACTIVE, DEAD, INJURED, ABSENT
    }
} 