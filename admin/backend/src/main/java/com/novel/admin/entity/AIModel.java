package com.novel.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_model")
public class AIModel {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("model_id")
    private String modelId;
    
    @TableField("display_name")
    private String displayName;
    
    private String provider;
    
    @TableField("api_base_url")
    private String apiBaseUrl;
    
    @TableField("api_key_ref")
    private String apiKeyRef;
    
    @TableField("max_tokens")
    private Integer maxTokens;
    
    @TableField("cost_per_1k")
    private Double costPer1k;
    
    @TableField("input_price_per_1k")
    private BigDecimal inputPricePer1k;
    
    @TableField("output_price_per_1k")
    private BigDecimal outputPricePer1k;
    
    @TableField("cost_multiplier")
    private BigDecimal costMultiplier;
    
    @TableField("temperature")
    private BigDecimal temperature;
    
    private Boolean available;
    
    @TableField("is_default")
    private Boolean isDefault;
    
    @TableField("sort_order")
    private Integer sortOrder;
    
    private String description;
    
    @TableField("created_at")
    private LocalDateTime createdAt;
    
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
