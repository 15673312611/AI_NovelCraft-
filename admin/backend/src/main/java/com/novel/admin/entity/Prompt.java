package com.novel.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("prompt_templates")
public class Prompt {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String content;
    private String type; // official-官方，custom-用户自定义
    private Long userId;
    private String category; // system_identity, writing_style, anti_ai, outline
    private String description;
    private Boolean isActive;
    private Boolean isDefault;
    private Integer sortOrder;
    private Integer usageCount;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
