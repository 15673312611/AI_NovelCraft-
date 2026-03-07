package com.novel.agentic.entity.graph;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 事件参与者实体（多对多关联）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("graph_event_participant")
public class GraphEventParticipant {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long eventId;
    
    private String characterName;
}
