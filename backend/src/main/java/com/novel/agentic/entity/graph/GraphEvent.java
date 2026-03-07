package com.novel.agentic.entity.graph;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 事件实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("graph_event")
public class GraphEvent {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long novelId;
    
    /** 业务ID */
    private String eventId;
    
    private Integer chapterNumber;
    
    private String summary;
    
    private String description;
    
    private String location;
    
    private String realm;
    
    private String emotionalTone;
    
    /** JSON数组 */
    private String tags;
    
    private Double importance;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
