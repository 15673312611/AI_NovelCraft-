package com.novel.agentic.entity.graph;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 冲突弧线实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("graph_conflict_arc")
public class GraphConflictArc {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long novelId;
    
    /** 业务ID */
    private String arcId;
    
    private String name;
    
    private String stage;
    
    private Double urgency;
    
    private String nextAction;
    
    private String protagonist;
    
    private String antagonist;
    
    private String trend;
    
    private Integer lastUpdatedChapter;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
