package com.novel.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.ResponseEntity;

/**
 * 认证工具类
 * 提供便捷的用户认证相关方法
 */
public class AuthUtils {

    /**
     * 获取当前登录用户ID
     * @return 用户ID，如果未登录返回null
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return SecurityUtils.getCurrentUserId(authentication);
    }

    /**
     * 获取当前登录用户名
     * @return 用户名，如果未登录返回null
     */
    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return SecurityUtils.getCurrentUsername(authentication);
    }

    /**
     * 检查用户是否已登录
     * @return 是否已登录
     */
    public static boolean isAuthenticated() {
        return getCurrentUserId() != null;
    }

    /**
     * 获取当前认证对象
     * @return Authentication对象
     */
    public static Authentication getCurrentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * 验证用户是否已登录，如果未登录返回401响应
     * @return 如果已登录返回null，如果未登录返回401响应
     */
    public static ResponseEntity<?> validateAuthentication() {
        if (!isAuthenticated()) {
            return ResponseEntity.status(401).body("用户未登录，请先登录");
        }
        return null;
    }

    /**
     * 验证用户权限，检查是否可以访问指定资源
     * @param resourceOwnerId 资源所有者ID
     * @return 如果有权限返回null，如果无权限返回403响应
     */
    public static ResponseEntity<?> validateResourceAccess(Long resourceOwnerId) {
        Authentication authentication = getCurrentAuthentication();
        if (!SecurityUtils.canAccessResource(authentication, resourceOwnerId)) {
            return ResponseEntity.status(403).body("无权限访问该资源");
        }
        return null;
    }

    /**
     * 检查是否为管理员
     * @return 是否为管理员
     */
    public static boolean isAdmin() {
        Authentication authentication = getCurrentAuthentication();
        return SecurityUtils.isAdmin(authentication);
    }
}
