package com.novel.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("user_credits")
public class UserCredit {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("user_id")
    private Long userId;
    
    private BigDecimal balance;
    
    @TableField("total_recharged")
    private BigDecimal totalRecharged;
    
    @TableField("total_consumed")
    private BigDecimal totalConsumed;
    
    @TableField("total_gifted")
    private BigDecimal totalGifted;
    
    @TableField("frozen_amount")
    private BigDecimal frozenAmount;
    
    @TableField("created_at")
    private LocalDateTime createdAt;
    
    @TableField("updated_at")
    private LocalDateTime updatedAt;
    
    // 关联用户信息（非数据库字段）
    @TableField(exist = false)
    private String username;
    
    @TableField(exist = false)
    private String email;
}
