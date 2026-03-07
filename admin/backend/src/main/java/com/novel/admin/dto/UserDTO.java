package com.novel.admin.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserDTO {
    private Long id;
    private String username;
    private String email;
    private String password;
    private String nickname;
    private String avatarUrl;
    private String roles; // Comma-separated role names from GROUP_CONCAT
    private String status;
    private Integer novelCount;
    private Integer aiTaskCount;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
}
