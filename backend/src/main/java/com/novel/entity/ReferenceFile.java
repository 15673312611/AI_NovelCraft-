package com.novel.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 参考文件实体
 * 支持txt和docx文件上传
 */
@Data
public class ReferenceFile {
    /**
     * 文件ID
     */
    private Long id;

    /**
     * 小说ID
     */
    private Long novelId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件类型: txt/docx
     */
    private String fileType;

    /**
     * 提取的文本内容
     */
    private String fileContent;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 原文件存储路径
     */
    private String originalPath;

    /**
     * 字数统计
     */
    private Integer wordCount;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}

