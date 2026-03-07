-- �?prompt_templates 表添加排序字�?
-- 执行此SQL以支持模板排序功�?

-- 添加 sort_order 字段
ALTER TABLE `prompt_templates`
ADD COLUMN `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序顺序，数字越小越靠前' AFTER `is_default`;

-- 为现有数据设置默认排序值（按创建时间倒序�?
SET @row_number = 0;
UPDATE `prompt_templates`
SET `sort_order` = (@row_number:=@row_number + 1)
ORDER BY `created_time` DESC;

-- 添加索引以提高排序查询性能
ALTER TABLE `prompt_templates` ADD INDEX `idx_sort_order` (`sort_order`);

-- 验证更新
SELECT id, name, category, is_default, sort_order, is_active
FROM `prompt_templates`
ORDER BY is_default DESC, sort_order ASC
LIMIT 10;
