package com.novel.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充值套餐实体
 */
@Data
@TableName("credit_packages")
public class CreditPackage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 套餐名称 */
    private String name;

    /** 价格 */
    private BigDecimal price;

    /** 包含字数点 */
    private Long credits;

    /** 描述 */
    private String description;

    /** 是否启用 */
    @TableField("is_active")
    private Boolean isActive;

    /** 排序 */
    @TableField("sort_order")
    private Integer sortOrder;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
