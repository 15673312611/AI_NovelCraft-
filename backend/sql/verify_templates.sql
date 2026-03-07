-- 验证模板配置�?SQL 脚本

-- 1. 检�?sort_order 字段是否存在
SELECT 
    COLUMN_NAME, 
    DATA_TYPE, 
    COLUMN_DEFAULT, 
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'ai_novel'
  AND TABLE_NAME = 'prompt_templates'
  AND COLUMN_NAME = 'sort_order';

-- 2. 查看所有模板的基本信息
SELECT 
    id,
    name,
    category,
    type,
    is_active,
    is_default,
    sort_order,
    usage_count,
    created_time
FROM prompt_templates
ORDER BY 
    category,
    is_default DESC,
    sort_order ASC,
    created_time DESC;

-- 3. 按分类统计模板数�?
SELECT 
    category,
    COUNT(*) as total_count,
    SUM(CASE WHEN type = 'official' THEN 1 ELSE 0 END) as official_count,
    SUM(CASE WHEN type = 'custom' THEN 1 ELSE 0 END) as custom_count,
    SUM(CASE WHEN is_default = 1 THEN 1 ELSE 0 END) as default_count,
    SUM(CASE WHEN is_active = 1 THEN 1 ELSE 0 END) as active_count
FROM prompt_templates
GROUP BY category
ORDER BY category;

-- 4. 查看 chapter 分类的模板（写作工作室使用）
SELECT 
    id,
    name,
    type,
    is_active,
    is_default,
    sort_order,
    usage_count,
    LEFT(content, 100) as content_preview
FROM prompt_templates
WHERE category = 'chapter'
  AND is_active = 1
ORDER BY 
    is_default DESC,
    sort_order ASC,
    type DESC,
    created_time DESC;

-- 5. 检查是否有重复的默认模板（每个分类应该只有一个）
SELECT 
    category,
    COUNT(*) as default_count,
    GROUP_CONCAT(id) as template_ids,
    GROUP_CONCAT(name) as template_names
FROM prompt_templates
WHERE is_default = 1
GROUP BY category
HAVING COUNT(*) > 1;

-- 6. 查看排序值分�?
SELECT 
    category,
    MIN(sort_order) as min_sort,
    MAX(sort_order) as max_sort,
    AVG(sort_order) as avg_sort,
    COUNT(DISTINCT sort_order) as unique_sort_values
FROM prompt_templates
GROUP BY category;
