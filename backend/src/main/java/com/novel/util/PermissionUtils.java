package com.novel.util;

import com.novel.domain.entity.Novel;
import org.springframework.stereotype.Component;

/**
 * 权限验证工具类
 */
@Component
public class PermissionUtils {

    /**
     * 验证用户是否有权限访问小说
     */
    public static void checkNovelPermission(Novel novel, Long userId) {
        if (novel == null) {
            throw new RuntimeException("小说不存在");
        }
        if (novel.getAuthorId() == null || !novel.getAuthorId().equals(userId)) {
            throw new RuntimeException("无权访问此小说");
        }
    }

    /**
     * 验证资源所有权
     */
    public static void checkOwnership(Long resourceOwnerId, Long currentUserId, String resourceName) {
        if (resourceOwnerId == null) {
            throw new RuntimeException(resourceName + "不存在");
        }
        if (!resourceOwnerId.equals(currentUserId)) {
            throw new RuntimeException("无权访问此" + resourceName);
        }
    }
}
