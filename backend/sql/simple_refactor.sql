-- =============================================
-- 简化版重构：测试环境直接删除重建
-- =============================================

-- 1. 清理 novel_document 表的章节数据
DELETE FROM novel_document WHERE document_type = 'chapter';

-- 2. 修改 novel_document 表：移除章节相关字段
ALTER TABLE novel_document 
DROP COLUMN IF EXISTS document_type,
DROP COLUMN IF EXISTS word_count;

-- 3. 修改 novel_folder 表：移除 folder_type
ALTER TABLE novel_folder 
DROP COLUMN IF EXISTS folder_type;

-- 4. 如果 ai_conversation 表有 document_id，改为 chapter_id
-- 先检查表结构，如果有 document_id 就执行下面的
ALTER TABLE ai_conversation
ADD COLUMN IF NOT EXISTS chapter_id BIGINT DEFAULT NULL COMMENT '关联的章节ID',
ADD INDEX IF NOT EXISTS idx_chapter_id (chapter_id);

-- 5. 更新 ai_conversation 的外键（如果存在 document_id）
-- 注意：由于删除了章节数据，这个外键关系会失效，直接删除旧数据
DELETE FROM ai_conversation WHERE document_id IS NOT NULL;

-- 6. 清理 novel_folder 和 novel_document 中的所有数据（测试环境可以删）
-- 如果你想保留辅助文档数据，就注释掉下面两行
TRUNCATE TABLE novel_document;
TRUNCATE TABLE novel_folder;

-- 完成！现在：
-- - chapters 表：存储所有章节（使用现有结构，不需要添加 folder_id）
-- - novel_document 表：只存储辅助文档（设定/角色/知识库）
-- - novel_folder 表：只存储辅助文档的文件夹

