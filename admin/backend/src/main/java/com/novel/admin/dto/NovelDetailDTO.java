package com.novel.admin.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NovelDetailDTO {
    private Long id;
    private String title;
    private String author;
    private String genre;
    private String status;
    private Integer wordCount;
    private Integer chapterCount;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
