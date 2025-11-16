-- ============================================================================
-- 修复 global_chapter_number 字段允许 NULL
-- ============================================================================
-- 问题：原建表语句中 global_chapter_number 设置为 NOT NULL，
--      但实际业务中该字段可能为空（当卷未设置起始章节号时）
-- 解决：修改字段为 NULL，允许空值
-- ============================================================================

-- 修复 volume_chapter_outlines 表
ALTER TABLE volume_chapter_outlines 
MODIFY COLUMN global_chapter_number INT NULL 
COMMENT '全书章节序号（从1开始，可选，若卷未设置起始章节则为NULL）';

-- 修复 foreshadow_lifecycle_log 表
ALTER TABLE foreshadow_lifecycle_log 
MODIFY COLUMN global_chapter_number INT NULL 
COMMENT '全书章节序号（可选）';

-- ============================================================================
-- 执行完成后，可以验证字段是否允许 NULL
-- ============================================================================
-- SHOW COLUMNS FROM volume_chapter_outlines LIKE 'global_chapter_number';
-- SHOW COLUMNS FROM foreshadow_lifecycle_log LIKE 'global_chapter_number';
-- ============================================================================

