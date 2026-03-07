package com.novel.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ai_tasks")
public class AITask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String type;
    private String status;
    private Integer progressPercentage;
    private Double actualCost;
    private Long userId;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
