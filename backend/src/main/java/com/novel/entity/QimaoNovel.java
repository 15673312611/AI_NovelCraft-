package com.novel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 七猫小说实体类
 */
@Data
@TableName("qimao_novels")
public class QimaoNovel {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 小说唯一标识
     */
    private String novelId;
    
    /**
     * 小说标题
     */
    private String title;
    
    /**
     * 作者
     */
    private String author;
    
    /**
     * 分类
     */
    private String category;
    
    /**
     * 子分类
     */
    private String subCategory;
    
    /**
     * 标签（JSON数组格式）
     */
    private String tags;
    
    /**
     * 小说简介
     */
    private String description;
    
    /**
     * 字数
     */
    private String wordCount;
    
    /**
     * 状态（连载中/完结）
     */
    private String status;
    
    /**
     * 更新时间
     */
    private String updateTime;
    
    /**
     * 第一章标题
     */
    private String firstChapterTitle;
    
    /**
     * 第一章内容
     */
    private String firstChapterContent;
    
    /**
     * 小说链接
     */
    private String novelUrl;
    
    /**
     * 作者链接
     */
    private String authorUrl;
    
    /**
     * 封面图片链接
     */
    private String coverImageUrl;
    
    /**
     * 排行榜位置
     */
    private Integer rankPosition;
    
    /**
     * 排行榜类型
     */
    private String rankType;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
