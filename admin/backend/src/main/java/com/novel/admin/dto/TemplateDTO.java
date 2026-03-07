package com.novel.admin.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TemplateDTO {
    private Long id;
    private String name;
    private String category;
    private String type; // official-官方，custom-用户自定义
    private String content;
    private String description;
    private Integer usageCount;
    private Integer favoriteCount; // 收藏数
    private Boolean isActive;
    private Boolean isDefault;
    private Integer sortOrder;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
