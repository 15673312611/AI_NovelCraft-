package com.novel.dto;

import lombok.Data;
import java.util.List;

/**
 * 七猫小说DTO
 */
@Data
public class QimaoNovelDTO {
    private String novelId;
    private String title;
    private String author;
    private String category;
    private String subCategory;
    private List<String> tags;
    private String description;
    private String wordCount;
    private String status;
    private String updateTime;
    private String firstChapterTitle;
    private String firstChapterContent;
    private String novelUrl;
    private String authorUrl;
    private String coverImageUrl;
    private Integer rankPosition;
    private String rankType;
}
