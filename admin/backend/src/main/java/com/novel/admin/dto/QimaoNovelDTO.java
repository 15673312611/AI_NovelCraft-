package com.novel.admin.dto;

import lombok.Data;

@Data
public class QimaoNovelDTO {
    private Long id;
    private String novelTitle;
    private String author;
    private String category;
    private Integer chapterCount; // 前端显示用，默认0
    private String status;
    private String scrapedAt;
    private String coverUrl;
    private String description;
    private String wordCount; // 字符串类型，如"123.4万字"
}
