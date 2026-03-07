-- 修复 qimao_categories 表结构，补充缺失的字�?
USE ai_novel;

-- 检查并添加 last_scrape_time 字段
SET @column_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'ai_novel' 
    AND TABLE_NAME = 'qimao_categories' 
    AND COLUMN_NAME = 'last_scrape_time'
);

SET @sql = IF(@column_exists = 0,
    'ALTER TABLE qimao_categories ADD COLUMN last_scrape_time TIMESTAMP NULL COMMENT ''最后爬取时�?' AFTER is_active',
    'SELECT ''Column last_scrape_time already exists'' AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查并添加 scrape_count 字段
SET @column_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'ai_novel' 
    AND TABLE_NAME = 'qimao_categories' 
    AND COLUMN_NAME = 'scrape_count'
);

SET @sql = IF(@column_exists = 0,
    'ALTER TABLE qimao_categories ADD COLUMN scrape_count INT DEFAULT 0 COMMENT ''爬取次数'' AFTER last_scrape_time',
    'SELECT ''Column scrape_count already exists'' AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加索引（如果不存在�?
SET @index_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE TABLE_SCHEMA = 'ai_novel' 
    AND TABLE_NAME = 'qimao_categories' 
    AND INDEX_NAME = 'idx_last_scrape_time'
);

SET @sql = IF(@index_exists = 0,
    'ALTER TABLE qimao_categories ADD INDEX idx_last_scrape_time (last_scrape_time)',
    'SELECT ''Index idx_last_scrape_time already exists'' AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT 'qimao_categories 表结构修复完�? AS result;
