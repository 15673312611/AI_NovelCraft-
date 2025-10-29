package com.novel.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * AI Generator Entity
 * Quản lý các loại generator và prompt template
 */
@Data
public class AiGenerator {
    /**
     * ID tự động tăng
     */
    private Long id;

    /**
     * Tên generator
     */
    private String name;

    /**
     * Mô tả ngắn về generator
     */
    private String description;

    /**
     * Icon class hoặc tên icon
     */
    private String icon;

    /**
     * Prompt template cho generator này
     */
    private String prompt;

    /**
     * Danh mục: writing, planning, character, general
     */
    private String category;

    /**
     * Thứ tự hiển thị
     */
    private Integer sortOrder;

    /**
     * Trạng thái: 1-active, 0-inactive
     */
    private Integer status;

    /**
     * Thời gian tạo
     */
    private LocalDateTime createdAt;

    /**
     * Thời gian cập nhật
     */
    private LocalDateTime updatedAt;
}

