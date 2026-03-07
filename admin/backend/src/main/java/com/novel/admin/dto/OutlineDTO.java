package com.novel.admin.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OutlineDTO {
    private Long id;
    private Long novelId;
    private String title;
    private String status;
    private Integer targetChapterCount;
    private String coreTheme;
    private String plotStructure;
    private LocalDateTime createdAt;
}
