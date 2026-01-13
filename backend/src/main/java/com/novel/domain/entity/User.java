package com.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 用户实体类
 * 
 * @author Novel Creation System
 * @version 1.0.0
 * @since 2024-01-01
 */
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "密码不能为空")
    private String password;

    private String nickname;

    @TableField("avatar_url")
    private String avatarUrl;

    private String bio;

    @NotNull(message = "用户状态不能为空")
    private UserStatus status = UserStatus.ACTIVE;

    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

    // 添加lastLoginTime字段用于兼容
    @TableField(exist = false)
    private LocalDateTime lastLoginTime;

    @TableField("email_verified")
    private Boolean emailVerified = false;

    @TableField("wechat_openid")
    private String wechatOpenid;

    @TableField("wechat_unionid")
    private String wechatUnionid;

    @TableField("login_type")
    private String loginType = "PASSWORD";

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // 构造函数
    public User() {}

    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public void setStatus(String status) {
        if (status != null) {
            try {
                this.status = UserStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                this.status = UserStatus.ACTIVE;
            }
        }
    }

    public String getStatusString() {
        return this.status != null ? this.status.name() : "ACTIVE";
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public LocalDateTime getLastLoginTime() {
        return lastLoginTime != null ? lastLoginTime : lastLoginAt;
    }

    public void setLastLoginTime(LocalDateTime lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
        this.lastLoginAt = lastLoginTime;
    }

    public Boolean getEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public String getWechatOpenid() {
        return wechatOpenid;
    }

    public void setWechatOpenid(String wechatOpenid) {
        this.wechatOpenid = wechatOpenid;
    }

    public String getWechatUnionid() {
        return wechatUnionid;
    }

    public void setWechatUnionid(String wechatUnionid) {
        this.wechatUnionid = wechatUnionid;
    }

    public String getLoginType() {
        return loginType;
    }

    public void setLoginType(String loginType) {
        this.loginType = loginType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // 业务方法

    public boolean isActive() {
        return UserStatus.ACTIVE.equals(this.status);
    }

    // 用户状态枚举
    public enum UserStatus {
        ACTIVE("活跃"),
        INACTIVE("非活跃"),
        SUSPENDED("暂停"),
        DELETED("已删除");

        private final String description;

        UserStatus(String description) {
            this.description = description;
    }

    public String getDescription() {
        return description;
    }
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", nickname='" + nickname + '\'' +
                ", status=" + status +
                ", createdAt=" + createdAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return id != null && id.equals(user.getId());
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
} 