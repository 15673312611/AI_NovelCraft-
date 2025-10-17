-- 修复 novel_character_profiles 表的 status 字段
-- 问题：status 字段是 ENUM 类型，无法存储长文本描述
-- 解决：将 status 改为 TEXT 类型

USE ai_novel;

-- 方案1：先删除有问题的索引（如果存在）
ALTER TABLE `novel_character_profiles` DROP INDEX IF EXISTS `idx_status_appearance`;

-- 方案2：修改 status 字段为 TEXT 类型
ALTER TABLE `novel_character_profiles` 
MODIFY COLUMN `status` TEXT COMMENT '角色状态描述';

-- 验证修改
SELECT 
    COLUMN_NAME,
    COLUMN_TYPE,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'ai_novel'
  AND TABLE_NAME = 'novel_character_profiles'
  AND COLUMN_NAME = 'status';
