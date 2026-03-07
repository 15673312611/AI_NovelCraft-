package com.novel.agentic.entity.graph;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 叙事节奏实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("graph_narrative_beat")
public class GraphNarrativeBeat {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long novelId;
    
    private String beatId;
    
    private Integer chapterNumber;
    
    private String beatType;
    
    private String focus;
    
    private String sentiment;
    
    private Double tension;
    
    private Double paceScore;
    
    private String viewpoint;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
