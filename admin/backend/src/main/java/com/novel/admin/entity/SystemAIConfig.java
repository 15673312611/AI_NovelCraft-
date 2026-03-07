package com.novel.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("system_ai_config")
public class SystemAIConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("config_key")
    private String configKey;
    
    @TableField("config_value")
    private String configValue;
    
    private String description;
    
    @TableField("is_encrypted")
    private Boolean isEncrypted;
    
    @TableField("created_at")
    private LocalDateTime createdAt;
    
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
