-- 最简单版本：直接修改 status 字段
-- 如果报错说索引问题，请先手动删除索引

USE ai_novel;

-- 直接修改字段类型
ALTER TABLE `novel_character_profiles` 
MODIFY COLUMN `status` TEXT COMMENT '角色状态描述';

