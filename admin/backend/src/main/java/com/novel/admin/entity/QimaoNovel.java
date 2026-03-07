package com.novel.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("qimao_novels")
public class QimaoNovel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String novelId;
    private String title;
    private String author;
    private String category;
    private String subCategory;
    private String tags;
    private String description;
    private String wordCount; // 字符串类型
    private String status;
    private String updateTime;
    private String firstChapterTitle;
    private String firstChapterContent;
    private String novelUrl;
    private String authorUrl;
    private String coverImageUrl;
    private Integer rankPosition;
    private String rankType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
