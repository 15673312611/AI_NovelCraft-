package com.novel.agentic.entity.graph;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 角色状态实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("graph_character_state")
public class GraphCharacterState {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long novelId;
    
    private String characterName;
    
    private String location;
    
    private String realm;
    
    @TableField("alive")
    private Boolean alive;
    
    private String affiliation;
    
    private String socialStatus;
    
    /** JSON数组 */
    private String backers;
    
    /** JSON数组 */
    private String tags;
    
    /** JSON数组 */
    private String secrets;
    
    /** JSON数组 */
    private String keyItems;
    
    /** JSON数组 */
    private String knownBy;
    
    /** JSON数组 */
    private String inventory;
    
    private String characterInfo;
    
    private Integer lastUpdatedChapter;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
