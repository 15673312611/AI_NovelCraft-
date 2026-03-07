package com.novel.admin.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChapterDTO {
    private Long id;
    private Long novelId;
    private String title;
    private Integer orderNum;
    private String status;
    private Integer wordCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
