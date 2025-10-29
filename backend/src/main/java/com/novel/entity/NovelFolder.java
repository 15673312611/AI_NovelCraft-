package com.novel.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 小说文件夹实体
 * 只用于辅助文档的文件夹管理（设定/角色/知识库等）
 * 章节不使用文件夹，直接存储在 chapters 表
 */
@Data
public class NovelFolder {
    /**
     * 文件夹ID
     */
    private Long id;

    /**
     * 小说ID
     */
    private Long novelId;

    /**
     * 文件夹名称
     */
    private String folderName;

    /**
     * 父文件夹ID（支持嵌套）
     */
    private Long parentId;

    /**
     * 排序顺序
     */
    private Integer sortOrder;

    /**
     * 是否为系统默认文件夹（不可删除、不可重命名）
     */
    private Boolean isSystem;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}

