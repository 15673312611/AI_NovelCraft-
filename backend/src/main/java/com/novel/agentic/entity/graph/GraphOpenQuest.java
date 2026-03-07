package com.novel.agentic.entity.graph;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 开放任务实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("graph_open_quest")
public class GraphOpenQuest {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long novelId;
    
    /** 业务ID */
    private String questId;
    
    private String description;
    
    private String status;
    
    private Integer introducedChapter;
    
    private Integer dueByChapter;
    
    private Integer resolvedChapter;
    
    private Integer lastUpdatedChapter;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
