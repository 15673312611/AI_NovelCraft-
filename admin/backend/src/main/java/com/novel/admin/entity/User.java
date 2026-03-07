package com.novel.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("users")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String email;
    private String password;
    private String role;  // 用户角色: USER/ADMIN
    private String nickname;
    private String avatarUrl;
    private String bio;
    private String status;
    private LocalDateTime lastLoginAt;
    private Boolean emailVerified;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Roles are stored in user_roles junction table, not in users table
    @TableField(exist = false)
    private List<String> roles;
}
