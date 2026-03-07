-- 修复模板类型：将 chapter 分类的模板从 custom 改为 official
-- 原因：后台管理添加的模板被错误地标记为用户自定义类型

UPDATE prompt_templates 
SET type = 'official'
WHERE category = 'chapter' 
  AND type = 'custom'
  AND user_id IS NULL;  -- 只修复没有关联用户的模板（真正的官方模板�?

-- 验证修复结果
SELECT 
    id,
    name,
    type,
    category,
    user_id,
    description
FROM prompt_templates
WHERE category = 'chapter'
ORDER BY id;
