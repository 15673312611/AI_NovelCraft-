-- 公告功能数据库配�?
-- 执行时间: 2026-01-08

-- 公告开�?
INSERT INTO system_ai_config (config_key, config_value, description, is_encrypted, created_at, updated_at)
VALUES ('announcement_enabled', 'false', '公告功能开�?, false, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- 公告标题
INSERT INTO system_ai_config (config_key, config_value, description, is_encrypted, created_at, updated_at)
VALUES ('announcement_title', '系统公告', '公告标题', false, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- 公告内容
INSERT INTO system_ai_config (config_key, config_value, description, is_encrypted, created_at, updated_at)
VALUES ('announcement_content', '', '公告内容（支持HTML�?, false, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- 公告更新时间戳（用于判断是否需要重新弹出）
INSERT INTO system_ai_config (config_key, config_value, description, is_encrypted, created_at, updated_at)
VALUES ('announcement_updated_at', '', '公告最后更新时�?, false, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();
