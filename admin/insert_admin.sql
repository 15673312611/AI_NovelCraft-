-- ============================================
-- 插入管理员账号
-- 用户名: admin
-- 密码: admin123
-- ============================================

-- 1. 确保 roles 表有 ADMIN 角色
INSERT IGNORE INTO `roles` (`id`, `name`, `description`, `permissions`) VALUES 
(2, 'ADMIN', '管理员', '["read", "write", "create_novel", "manage_users", "manage_system", "view_dashboard"]');

-- 2. 插入管理员用户（密码: admin123）
-- BCrypt 加密后的密码
INSERT INTO `users` (`username`, `email`, `password`, `role`, `nickname`, `status`, `created_at`, `updated_at`) 
VALUES ('admin', 'admin@novel.com', '$2a$10$EIxjDuH1ZdNdT.F1h3nU5uQRxFkWHr0OHd6n8i8xqGQKQkY/3JWES', 'ADMIN', '系统管理员', 'ACTIVE', NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    `password` = '$2a$10$EIxjDuH1ZdNdT.F1h3nU5uQRxFkWHr0OHd6n8i8xqGQKQkY/3JWES',
    `role` = 'ADMIN',
    `status` = 'ACTIVE',
    `updated_at` = NOW();

-- 3. 关联管理员到 ADMIN 角色
INSERT IGNORE INTO `user_roles` (`user_id`, `role_id`) 
SELECT u.id, r.id FROM users u, roles r 
WHERE u.username = 'admin' AND r.name = 'ADMIN';

-- 4. 验证插入结果
SELECT u.id, u.username, u.email, u.role, u.status, 
       GROUP_CONCAT(r.name) as roles 
FROM users u 
LEFT JOIN user_roles ur ON u.id = ur.user_id 
LEFT JOIN roles r ON ur.role_id = r.id 
WHERE u.username = 'admin' 
GROUP BY u.id;
