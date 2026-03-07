-- ============================================
-- 后台管理系统初始化脚本
-- 用于初始化角色表和默认管理员账号
-- ============================================

-- 初始化角色数据（如果不存在）
INSERT IGNORE INTO `roles` (`id`, `name`, `description`, `permissions`) VALUES 
(1, 'USER', '普通用户', '["read", "write", "create_novel"]'),
(2, 'ADMIN', '管理员', '["read", "write", "create_novel", "manage_users", "manage_system", "view_dashboard"]'),
(3, 'VIP', 'VIP用户', '["read", "write", "create_novel", "unlimited_ai"]');

-- ============================================
-- 创建默认管理员账号
-- 用户名: admin
-- 密码: admin123
-- 密码哈希由 BCrypt 生成
-- ============================================

-- 如果需要使用不同的密码，请：
-- 1. 启动后台服务
-- 2. 访问 http://localhost:8081/dev/encode-password?password=你的密码
-- 3. 将返回的 encodedPassword 替换下面的哈希值

-- 注意：密码 'admin123' 的 BCrypt 哈希（每次生成不同，但都能验证通过）
-- 以下哈希对应密码 'admin123'
INSERT IGNORE INTO `users` (`id`, `username`, `email`, `password`, `role`, `nickname`, `status`, `created_at`, `updated_at`) 
VALUES (1, 'admin', 'admin@novel.com', '$2a$10$EIxjDuH1ZdNdT.F1h3nU5uQRxFkWHr0OHd6n8i8xqGQKQkY/3JWES', 'ADMIN', '系统管理员', 'ACTIVE', NOW(), NOW())
ON DUPLICATE KEY UPDATE `role` = 'ADMIN';

-- 关联管理员到 ADMIN 角色（使用 INSERT IGNORE 避免重复插入错误）
INSERT IGNORE INTO `user_roles` (`user_id`, `role_id`) 
SELECT u.id, r.id FROM users u, roles r 
WHERE u.username = 'admin' AND r.name = 'ADMIN';

-- ============================================
-- 验证初始化结果
-- ============================================
-- SELECT u.username, u.role, GROUP_CONCAT(r.name) as roles 
-- FROM users u 
-- LEFT JOIN user_roles ur ON u.id = ur.user_id 
-- LEFT JOIN roles r ON ur.role_id = r.id 
-- WHERE u.username = 'admin' 
-- GROUP BY u.id;
