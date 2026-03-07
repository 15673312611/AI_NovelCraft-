package com.novel.admin.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OperationLogDTO {
    private Long id;
    private String username;
    private String action;
    private String module;
    private String ip;
    private LocalDateTime createdAt;
}
