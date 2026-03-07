package com.novel.admin.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NovelDTO {
    private Long id;
    private String title;
    private String author;
    private String genre;
    private String status;
    private Integer chapterCount;
    private Integer wordCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
