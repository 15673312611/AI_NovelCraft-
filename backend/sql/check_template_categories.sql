-- 查询所有模板的分类统计
SELECT 
    category,
    type,
    COUNT(*) as count,
    GROUP_CONCAT(name SEPARATOR ', ') as template_names
FROM prompt_templates
WHERE is_active = 1
GROUP BY category, type
ORDER BY category, type;

-- 查询所有模板的详细信息
SELECT 
    id,
    name,
    type,
    category,
    is_active,
    is_default,
    description
FROM prompt_templates
WHERE is_active = 1
ORDER BY category, type, name;

-- 专门查询 chapter 分类的模�?
SELECT 
    id,
    name,
    type,
    category,
    description
FROM prompt_templates
WHERE category = 'chapter'
  AND is_active = 1;
