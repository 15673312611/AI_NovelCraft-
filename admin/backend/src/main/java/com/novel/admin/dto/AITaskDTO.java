package com.novel.admin.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AITaskDTO {
    private Long id;
    private String name;
    private String type;
    private String status;
    private Integer progress;
    private Double cost;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String username;
}
