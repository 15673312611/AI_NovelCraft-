-- 为  表添加新字段

-- 添加 is_default 字段（是否默认）
ALTER TABLE `` 
ADD COLUMN `is_default` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否默认：1-默认，0-非默认' AFTER `is_active`;

-- 更新 category 字段注释（添加 outline 分类说明）
ALTER TABLE `` 
MODIFY COLUMN `category` VARCHAR(50) NULL COMMENT '分类：system_identity-系统身份，writing_style-写作风格，anti_ai-去AI味，outline-大纲生成';





