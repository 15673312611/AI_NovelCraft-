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
     * 
     * 重要提示：
     * - official: 官方模板，所有用户可见，user_id 为 NULL
     * - custom: 用户自定义模板，仅创建者可见，user_id 不为 NULL
     * 
     * 注意：后台管理系统添加的模板应设置为 'official' 类型！
     */
    private String type;
    
    /**
     * 用户ID（官方模板为NULL）
     * 
     * 重要提示：
     * - 如果 type='official'，则 user_id 必须为 NULL
     * - 如果 type='custom'，则 user_id 必须有值
     */
    private Long userId;
    
    /**
     * 分类：
     * - system_identity: 系统身份模板
     * - writing_style: 写作风格模板
     * - anti_ai: 去AI味模板
     * - chapter: 章节写作模板（用于AI辅助写作）
     * - outline: 大纲生成模板
     * - character: 角色设定模板
     * - worldbuilding: 世界设定模板
     * - tool: 工具类模板
     * - brainstorm: 脑洞创意模板
     * - title_synopsis: 书名简介模板
     * - cover: 封面模板
     * - other: 其他类型
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
     * 是否默认：true-默认，false-非默认
     */
    private Boolean isDefault;

    /**
     * 排序顺序，数字越小越靠前
     */
    private Integer sortOrder;

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

