package com.novel.shortstory.dto;

import lombok.Data;

@Data
public class CreateShortStoryRequest {
    private String title;
    private String idea;
    private Integer targetWords;
    private Integer chapterCount;
    private Boolean enableOutlineUpdate;
    private Integer minPassScore;
    
    /**
     * AI模型ID（可选，为空使用默认模型）
     */
    private String modelId;
}
