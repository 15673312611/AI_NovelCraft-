-- 为 novel_outlines 表添加核心设定字段
-- 用于存储从完整大纲中提炼的核心设定信息（不包含具体剧情）

ALTER TABLE novel_outlines 
ADD COLUMN core_settings LONGTEXT COMMENT '核心设定（从大纲提炼，不含具体剧情）' AFTER plot_structure;

-- 添加索引以便快速查询
ALTER TABLE novel_outlines 
ADD INDEX idx_core_settings_exists ((CASE WHEN core_settings IS NOT NULL AND core_settings != '' THEN 1 ELSE 0 END));

