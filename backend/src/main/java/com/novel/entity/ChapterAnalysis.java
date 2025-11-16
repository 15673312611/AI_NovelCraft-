package com.novel.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChapterAnalysis {
    private Long id;
    private Long novelId;
    private String analysisType;
    private Integer startChapter;
    private Integer endChapter;
    private String analysisContent;
    private Integer wordCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

