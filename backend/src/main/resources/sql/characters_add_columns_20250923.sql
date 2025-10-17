-- MySQL 8.0+ migration: add missing columns used by application code to `characters`
-- Safe-guards use IF NOT EXISTS so it can be run multiple times.

ALTER TABLE `characters`
  ADD COLUMN IF NOT EXISTS `character_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT 'MINOR' COMMENT '角色类型: PROTAGONIST/MAJOR/MINOR/TEMPORARY',
  ADD COLUMN IF NOT EXISTS `importance_score` int DEFAULT 0 COMMENT '重要性评分(0-100)',
  ADD COLUMN IF NOT EXISTS `is_protagonist` tinyint(1) DEFAULT 0 COMMENT '是否主角',
  ADD COLUMN IF NOT EXISTS `is_antagonist` tinyint(1) DEFAULT 0 COMMENT '是否反派',
  ADD COLUMN IF NOT EXISTS `is_major_character` tinyint(1) DEFAULT 0 COMMENT '是否重要角色',
  ADD COLUMN IF NOT EXISTS `first_appearance_chapter` int NULL COMMENT '首次出现章',
  ADD COLUMN IF NOT EXISTS `last_appearance_chapter` int NULL COMMENT '最近出现章',
  ADD COLUMN IF NOT EXISTS `appearance_count` int DEFAULT 0 COMMENT '出场次数',
  ADD COLUMN IF NOT EXISTS `status` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT 'ACTIVE' COMMENT '角色状态: ACTIVE/INACTIVE/DECEASED/MISSING',
  ADD COLUMN IF NOT EXISTS `alias` varchar(255) COLLATE utf8mb4_unicode_ci NULL COMMENT '别名',
  ADD COLUMN IF NOT EXISTS `tags` varchar(255) COLLATE utf8mb4_unicode_ci NULL COMMENT '标签(逗号分隔)',
  ADD COLUMN IF NOT EXISTS `character_image_url` varchar(512) COLLATE utf8mb4_unicode_ci NULL COMMENT '角色头像URL',
  ADD COLUMN IF NOT EXISTS `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ADD COLUMN IF NOT EXISTS `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- Helpful indexes (IF NOT EXISTS is supported in MySQL 8.0+ via CREATE INDEX)
CREATE INDEX IF NOT EXISTS `idx_characters_novel_id` ON `characters`(`novel_id`);
CREATE INDEX IF NOT EXISTS `idx_characters_type` ON `characters`(`character_type`);
CREATE INDEX IF NOT EXISTS `idx_characters_updated_at` ON `characters`(`updated_at`);

-- Optional: normalize existing NULLs
UPDATE `characters` SET `character_type` = COALESCE(`character_type`, 'MINOR');
UPDATE `characters` SET `importance_score` = COALESCE(`importance_score`, 0);
UPDATE `characters` SET `status` = COALESCE(`status`, 'ACTIVE');
UPDATE `characters` SET `appearance_count` = COALESCE(`appearance_count`, 0);

