-- AI形容词库表（用于检测AI痕迹的可疑词汇）
CREATE TABLE IF NOT EXISTS `ai_adjectives` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `word` VARCHAR(64) NOT NULL COMMENT '形容词/可疑词条',
  `lang` VARCHAR(16) NOT NULL DEFAULT 'zh-CN' COMMENT '语言',
  `category` VARCHAR(32) NOT NULL DEFAULT 'adjective' COMMENT '类别',
  `hash` CHAR(64) NULL COMMENT '去重哈希(可选)',
  `source_model` VARCHAR(64) NULL COMMENT '来源模型',
  `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_word_lang` (`word`, `lang`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI可疑形容词库';

-- 原始采集明细表（不去重，用于统计频次/来源等）
CREATE TABLE IF NOT EXISTS `ai_adjectives_raw` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `word` VARCHAR(64) NOT NULL COMMENT '词条',
  `lang` VARCHAR(16) NOT NULL DEFAULT 'zh-CN' COMMENT '语言',
  `category` VARCHAR(32) NOT NULL COMMENT '类别',
  `source_model` VARCHAR(64) NULL COMMENT '来源模型',
  `batch_id` VARCHAR(64) NULL COMMENT '本次采集批次ID',
  `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_cat_time` (`category`,`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI可疑词条采集明细(不去重)';


