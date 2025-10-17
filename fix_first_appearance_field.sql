-- 修复 novel_character_profiles 表的 first_appearance 字段
-- 为其添加默认值，避免插入时报错

USE ai_novel;

-- 修改 first_appearance 字段，添加默认值为 NULL
ALTER TABLE `novel_character_profiles`
MODIFY COLUMN `first_appearance` INT DEFAULT NULL COMMENT '首次出现的章节号';

-- 如果你希望设置默认值为 0，可以使用下面的语句：
-- ALTER TABLE `novel_character_profiles`
-- MODIFY COLUMN `first_appearance` INT DEFAULT 0 COMMENT '首次出现的章节号';

