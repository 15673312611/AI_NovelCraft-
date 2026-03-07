-- 每日免费字数功能数据库迁移脚�?
-- 执行时间: 2026-01-08

-- 1. �?user_credits 表中添加每日免费字数相关字段
ALTER TABLE user_credits 
ADD COLUMN daily_free_balance DECIMAL(20,4) DEFAULT 0 COMMENT '今日剩余免费字数',
ADD COLUMN daily_free_last_reset DATE DEFAULT NULL COMMENT '上次重置日期';

-- 2. �?system_ai_config 表中添加每日免费字数配置
-- 每日免费字数开�?
INSERT INTO system_ai_config (config_key, config_value, description, is_encrypted, created_at, updated_at)
VALUES ('daily_free_credits_enabled', 'true', '每日免费字数功能开�?, false, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- 每日免费字数数量
INSERT INTO system_ai_config (config_key, config_value, description, is_encrypted, created_at, updated_at)
VALUES ('daily_free_credits_amount', '50000', '每日免费字数数量', false, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- 3. �?credit_transactions 表中添加交易来源字段，区分免费字数和字数包消�?
ALTER TABLE credit_transactions 
ADD COLUMN credit_source VARCHAR(20) DEFAULT 'PACKAGE' COMMENT '字数来源: DAILY_FREE=每日免费, PACKAGE=字数�?;

-- 4. 创建索引优化查询
CREATE INDEX idx_user_credits_daily_reset ON user_credits(daily_free_last_reset);
CREATE INDEX idx_credit_transactions_source ON credit_transactions(credit_source);
