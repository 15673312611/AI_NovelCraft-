-- MySQL 8.0+ CREATE TABLE for `characters`
-- 如果已存在旧表，请先备份数据，再按需 DROP TABLE 或跳过本脚本。
-- 为安全起见，这里使用 IF NOT EXISTS。

CREATE TABLE IF NOT EXISTS `characters` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
  `alias` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '别名',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '人物描述',
  `appearance` text COLLATE utf8mb4_unicode_ci COMMENT '外貌',
  `personality` text COLLATE utf8mb4_unicode_ci COMMENT '性格',
  `background` text COLLATE utf8mb4_unicode_ci COMMENT '背景',
  `motivation` text COLLATE utf8mb4_unicode_ci COMMENT '动机',
  `goals` text COLLATE utf8mb4_unicode_ci COMMENT '目标',
  `conflicts` text COLLATE utf8mb4_unicode_ci COMMENT '冲突',
  `relationships` text COLLATE utf8mb4_unicode_ci COMMENT '关系网',
  `character_arc` text COLLATE utf8mb4_unicode_ci COMMENT '角色弧光',
  `is_protagonist` tinyint(1) DEFAULT 0 COMMENT '是否主角',
  `is_antagonist` tinyint(1) DEFAULT 0 COMMENT '是否反派',
  `is_major_character` tinyint(1) DEFAULT 0 COMMENT '是否重要角色',
  `first_appearance_chapter` int DEFAULT NULL COMMENT '首次出现章',
  `last_appearance_chapter` int DEFAULT NULL COMMENT '最近出现章',
  `appearance_count` int DEFAULT 0 COMMENT '出场次数',
  `character_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT 'MINOR' COMMENT 'PROTAGONIST/MAJOR/MINOR/TEMPORARY',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE/DECEASED/MISSING',
  `importance_score` int DEFAULT 0 COMMENT '重要性评分(0-100)',
  `tags` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标签',
  `character_image_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '角色头像URL',
  `novel_id` bigint NOT NULL COMMENT '所属小说ID',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_characters_novel_id` (`novel_id`),
  KEY `idx_characters_type` (`character_type`),
  KEY `idx_characters_updated_at` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小说角色表';

