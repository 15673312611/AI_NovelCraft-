package com.novel.agentic.entity.graph;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 关系状态实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("graph_relationship_state")
public class GraphRelationshipState {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long novelId;
    
    /** 按字典序较小的角色名 */
    private String characterA;
    
    /** 按字典序较大的角色名 */
    private String characterB;
    
    private String type;
    
    private Double strength;
    
    private String description;
    
    private String publicStatus;
    
    private Integer lastUpdatedChapter;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
