package com.novel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 七猫分类配置实体类
 */
@Data
@TableName("qimao_categories")
public class QimaoCategory {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 分类名称
     */
    private String categoryName;
    
    /**
     * 分类代码
     */
    private String categoryCode;
    
    /**
     * 分类URL
     */
    private String categoryUrl;
    
    /**
     * 父分类
     */
    private String parentCategory;
    
    /**
     * 排序
     */
    private Integer sortOrder;
    
    /**
     * 是否启用
     */
    private Boolean isActive;
    
    /**
     * 最后爬取时间
     */
    private LocalDateTime lastScrapeTime;
    
    /**
     * 爬取次数
     */
    private Integer scrapeCount;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
