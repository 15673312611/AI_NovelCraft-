package com.novel.admin.dto;

import lombok.Data;

@Data
public class VolumeDTO {
    private Long id;
    private Long novelId;
    private String title;
    private Integer volumeNumber;
    private Integer chapterStart;
    private Integer chapterEnd;
    private String status;
    private String theme;
    private String description;
    private String contentOutline;
    private Integer estimatedWordCount;
    private Integer actualWordCount;
    private String createdAt;
    private String updatedAt;
}
