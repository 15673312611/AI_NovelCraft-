package com.novel.admin.dto;

import lombok.Data;

@Data
public class ChapterPlanDTO {
    private Long id;
    private Long novelId;
    private Integer chapterNumber;
    private String title;
    private String phase;
    private String status;
    private String priority;
    private String mainGoal;
}
