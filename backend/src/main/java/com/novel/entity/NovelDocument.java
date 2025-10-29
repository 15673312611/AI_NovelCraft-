package com.novel.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 小说文档实体
 * 只管理辅助文档（设定/角色/知识库等）
 * 章节使用 chapters 表管理
 */
@Data
public class NovelDocument {
    /**
     * 文档ID
     */
    private Long id;

    /**
     * 小说ID
     */
    private Long novelId;

    /**
     * 所属文件夹ID
     */
    private Long folderId;

    /**
     * 文档标题
     */
    private String title;

    /**
     * 文档内容
     */
    private String content;

    /**
     * 排序顺序
     */
    private Integer sortOrder;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}

