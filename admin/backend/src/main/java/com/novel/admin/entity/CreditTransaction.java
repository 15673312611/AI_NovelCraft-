package com.novel.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("credit_transactions")
public class CreditTransaction {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("user_id")
    private Long userId;
    
    private String type;
    
    private BigDecimal amount;
    
    @TableField("balance_before")
    private BigDecimal balanceBefore;
    
    @TableField("balance_after")
    private BigDecimal balanceAfter;
    
    @TableField("ai_task_id")
    private Long aiTaskId;
    
    @TableField("model_id")
    private String modelId;
    
    @TableField("input_tokens")
    private Integer inputTokens;
    
    @TableField("output_tokens")
    private Integer outputTokens;
    
    private String description;
    
    @TableField("operator_id")
    private Long operatorId;
    
    @TableField("created_at")
    private LocalDateTime createdAt;
    
    // 关联信息
    @TableField(exist = false)
    private String username;
    
    @TableField(exist = false)
    private String operatorName;
}
