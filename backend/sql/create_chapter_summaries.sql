-- 章节概括表
CREATE TABLE IF NOT EXISTS `chapter_summaries` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `novel_id` bigint(20) NOT NULL COMMENT '小说ID',
  `chapter_number` int(11) NOT NULL COMMENT '章节号',
  `summary` text NOT NULL COMMENT '章节概括内容（100-200字）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_novel_chapter` (`novel_id`, `chapter_number`),
  KEY `idx_novel_id` (`novel_id`),
  KEY `idx_chapter_number` (`chapter_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='章节概括表';