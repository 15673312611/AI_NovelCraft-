package com.novel.agentic.entity.graph;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 世界规则实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("graph_world_rule")
public class GraphWorldRule {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long novelId;
    
    /** 业务ID */
    private String ruleId;
    
    private String name;
    
    private String content;
    
    @TableField("constraint_text")
    private String constraintText;
    
    private String category;
    
    private String scope;
    
    private Double importance;
    
    private Integer introducedAt;
    
    private Integer applicableChapter;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
