package com.novel.admin.dto;

import lombok.Data;

@Data
public class WorldviewDTO {
    private Long id;
    private Long novelId;
    private String term;
    private String type;
    private Integer firstMention;
    private String description;
    private String contextInfo;
    private Integer usageCount;
    private Boolean isImportant;
    private String createdTime;
    private String updatedTime;
}
