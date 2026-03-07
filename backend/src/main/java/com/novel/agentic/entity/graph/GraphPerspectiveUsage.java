package com.novel.agentic.entity.graph;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 视角使用实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("graph_perspective_usage")
public class GraphPerspectiveUsage {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long novelId;
    
    private String perspectiveId;
    
    private Integer chapterNumber;
    
    private String characterName;
    
    private String mode;
    
    private String tone;
    
    private String purpose;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
