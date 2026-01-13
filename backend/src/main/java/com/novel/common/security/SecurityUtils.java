package com.novel.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/**
 * 安全工具类
 * 提供用户认证和权限相关的工具方法
 */
public class SecurityUtils {

    /**
     * 获取当前登录用户ID
     * @param authentication 认证对象
     * @return 用户ID
     */
    public static Long getCurrentUserId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        
        Object principal = authentication.getPrincipal();
        
        // 优先支持增强版用户主体（直接包含用户ID）
        if (principal instanceof com.novel.config.EnhancedJwtAuthenticationFilter.EnhancedUserPrincipal) {
            return ((com.novel.config.EnhancedJwtAuthenticationFilter.EnhancedUserPrincipal) principal).getUserId();
        }
        
        // 支持自定义用户主体
        if (principal instanceof com.novel.service.CustomUserDetailsService.CustomUserPrincipal) {
            return ((com.novel.service.CustomUserDetailsService.CustomUserPrincipal) principal).getUserId();
        }
        
        // 支持旧版用户主体
        if (principal instanceof UserPrincipal) {
            return ((UserPrincipal) principal).getUserId();
        }
        
        // 如果是其他类型的Principal，尝试从name获取ID（不推荐）
        if (principal instanceof UserDetails) {
            try {
                return Long.parseLong(((UserDetails) principal).getUsername());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        return null;
    }

    /**
     * 获取当前登录用户名
     * @param authentication 认证对象
     * @return 用户名
     */
    public static String getCurrentUsername(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal) {
            return ((UserPrincipal) principal).getUsername();
        }
        
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        }
        
        return authentication.getName();
    }

    /**
     * 获取当前用户的角色
     * @param authentication 认证对象
     * @return 角色列表
     */
    public static java.util.Set<String> getCurrentUserRoles(Authentication authentication) {
        if (authentication == null) {
            return java.util.Collections.emptySet();
        }
        
        return authentication.getAuthorities().stream()
            .map(authority -> authority.getAuthority())
            .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 检查用户是否有指定权限
     * @param authentication 认证对象
     * @param permission 权限名称
     * @return 是否有权限
     */
    public static boolean hasPermission(Authentication authentication, String permission) {
        if (authentication == null) {
            return false;
        }
        
        return authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals(permission) 
                || authority.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * 检查是否为管理员
     * @param authentication 认证对象
     * @return 是否为管理员
     */
    public static boolean isAdmin(Authentication authentication) {
        return hasPermission(authentication, "ROLE_ADMIN");
    }

    /**
     * 检查用户是否可以访问指定资源
     * @param authentication 认证对象
     * @param resourceOwnerId 资源所有者ID
     * @return 是否可以访问
     */
    public static boolean canAccessResource(Authentication authentication, Long resourceOwnerId) {
        if (authentication == null || resourceOwnerId == null) {
            return false;
        }
        
        // 管理员可以访问所有资源
        if (isAdmin(authentication)) {
            return true;
        }
        
        // 用户只能访问自己的资源
        Long currentUserId = getCurrentUserId(authentication);
        return currentUserId != null && resourceOwnerId.equals(currentUserId);
    }

    /**
     * 验证用户权限，如果没有权限则抛出异常
     * @param authentication 认证对象
     * @param resourceOwnerId 资源所有者ID
     * @throws SecurityException 权限异常
     */
    public static void validateResourceAccess(Authentication authentication, Long resourceOwnerId) 
            throws SecurityException {
        if (!canAccessResource(authentication, resourceOwnerId)) {
            throw new SecurityException("无权限访问该资源");
        }
    }

    /**
     * 用户主体信息类
     */
    public static class UserPrincipal implements UserDetails {
        private Long userId;
        private String username;
        private String password;
        private java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> authorities;
        private boolean accountNonExpired = true;
        private boolean accountNonLocked = true;
        private boolean credentialsNonExpired = true;
        private boolean enabled = true;

        public UserPrincipal(Long userId, String username, String password, 
                            java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> authorities) {
            this.userId = userId;
            this.username = username;
            this.password = password;
            this.authorities = authorities;
        }

        public Long getUserId() {
            return userId;
        }

        @Override
        public String getUsername() {
            return username;
        }

        @Override
        public String getPassword() {
            return password;
        }

        @Override
        public java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
            return authorities;
        }

        @Override
        public boolean isAccountNonExpired() {
            return accountNonExpired;
        }

        @Override
        public boolean isAccountNonLocked() {
            return accountNonLocked;
        }

        @Override
        public boolean isCredentialsNonExpired() {
            return credentialsNonExpired;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }
    }
}