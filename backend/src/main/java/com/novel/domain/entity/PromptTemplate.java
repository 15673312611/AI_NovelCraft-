package com.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提示词模板实体
 */
@Data
@TableName("prompt_templates")
public class PromptTemplate {
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 模板名称
     */
    private String name;
    
    /**
     * 提示词内容
     */
    private String content;
    
    /**
     * 模板类型：official-官方，custom-用户自定义
     */
    private String type;
    
    /**
     * 用户ID（官方模板为NULL）
     */
    private Long userId;
    
    /**
     * 分类：system_identity-系统身份，writing_style-写作风格，anti_ai-去AI味
     */
    private String category;
    
    /**
     * 模板描述
     */
    private String description;
    
    /**
     * 是否启用：true-启用，false-禁用
     */
    private Boolean isActive;
    
    /**
     * 使用次数
     */
    private Integer usageCount;
    
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
    
    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
    
    /**
     * 是否已收藏（非数据库字段）
     */
    @TableField(exist = false)
    private Boolean isFavorited;
}

