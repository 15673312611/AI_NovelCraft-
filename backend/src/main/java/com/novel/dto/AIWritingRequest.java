package com.novel.dto;

import lombok.Data;

@Data
public class AIWritingRequest {
    private Long novelId;
    private String writingType;

    private String genre;
    private String theme;

    private String characterName;
    private String characterRole;
    private String role;
    private String background;

    private String chapterTitle;
    private String chapterOutline;
    private String outline;
    private String previousContent;
}

