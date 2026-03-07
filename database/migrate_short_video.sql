-- ============================================
-- 线上数据库一次性迁移脚本
-- 包含：短篇小说 + 视频剧本 相关表和字段
-- 执行方式：mysql -u root -p ai_novel < migrate_short_video.sql
-- ============================================

-- ============================================
-- 1. 短篇小说相关表
-- ============================================

-- 1.1 短篇小说主表
CREATE TABLE IF NOT EXISTS short_novels (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    idea TEXT,
    outline TEXT,
    target_words INT DEFAULT 30000,
    chapter_count INT DEFAULT 10,
    words_per_chapter INT DEFAULT 3000,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    current_chapter INT DEFAULT 0,
    current_retry_count INT DEFAULT 0,
    max_retry_per_chapter INT DEFAULT 3,
    min_pass_score INT DEFAULT 7,
    enable_outline_update TINYINT(1) DEFAULT 1,
    workflow_config TEXT,
    error_message TEXT,
    total_words INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1.2 短篇小说章节表
CREATE TABLE IF NOT EXISTS short_novel_chapters (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    novel_id BIGINT NOT NULL,
    chapter_number INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    brief TEXT,
    content TEXT,
    word_count INT DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    review_result TEXT,
    generation_time BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_novel_id (novel_id),
    INDEX idx_novel_chapter (novel_id, chapter_number),
    FOREIGN KEY (novel_id) REFERENCES short_novels(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1.3 工作流日志表
CREATE TABLE IF NOT EXISTS workflow_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    novel_id BIGINT NOT NULL,
    chapter_number INT,
    type VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    detail TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_novel_id (novel_id),
    INDEX idx_novel_chapter (novel_id, chapter_number),
    FOREIGN KEY (novel_id) REFERENCES short_novels(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1.4 补充短篇小说扩展字段（如果表已存在则添加字段）
-- story_setting, active_step, hooks_json, prologue

-- 安全添加字段的存储过程
DELIMITER //
CREATE PROCEDURE add_column_if_not_exists(
    IN table_name VARCHAR(100),
    IN column_name VARCHAR(100),
    IN column_definition VARCHAR(500)
)
BEGIN
    DECLARE col_exists INT DEFAULT 0;
    SELECT COUNT(*) INTO col_exists 
    FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = table_name 
      AND COLUMN_NAME = column_name;
    
    IF col_exists = 0 THEN
        SET @sql = CONCAT('ALTER TABLE ', table_name, ' ADD COLUMN ', column_name, ' ', column_definition);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- 添加 short_novels 缺失字段
CALL add_column_if_not_exists('short_novels', 'story_setting', 'TEXT AFTER idea');
CALL add_column_if_not_exists('short_novels', 'hooks_json', 'TEXT AFTER outline');
CALL add_column_if_not_exists('short_novels', 'prologue', 'TEXT AFTER hooks_json');
CALL add_column_if_not_exists('short_novels', 'active_step', 'VARCHAR(100) NULL AFTER status');

-- 添加 short_novel_chapters 缺失字段
CALL add_column_if_not_exists('short_novel_chapters', 'last_adjustment', 'TEXT AFTER brief');
CALL add_column_if_not_exists('short_novel_chapters', 'analysis_result', 'TEXT AFTER review_result');

-- ============================================
-- 2. 视频剧本相关表
-- ============================================

-- 2.1 视频剧本主表
CREATE TABLE IF NOT EXISTS video_scripts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    idea TEXT,

    -- 工作流参数
    mode VARCHAR(50) NOT NULL DEFAULT 'HALF_NARRATION',
    target_seconds INT DEFAULT 60,
    scene_count INT DEFAULT 20,

    -- 工作流产物
    script_setting TEXT,
    storyboard TEXT,
    final_script TEXT,

    -- 工作流状态
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    active_step VARCHAR(100) NULL,
    workflow_config TEXT,
    error_message TEXT,

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_user_id (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2.2 视频剧本日志表
CREATE TABLE IF NOT EXISTS video_script_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    detail TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_script_id (script_id),
    FOREIGN KEY (script_id) REFERENCES video_scripts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2.3 视频剧本集数表
CREATE TABLE IF NOT EXISTS video_script_episodes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT NOT NULL,
    episode_number INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    brief TEXT,
    last_adjustment TEXT,
    storyboard TEXT,
    content TEXT,
    word_count INT DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    review_result TEXT,
    analysis_result TEXT,
    generation_time BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_script_id (script_id),
    INDEX idx_script_episode (script_id, episode_number),
    FOREIGN KEY (script_id) REFERENCES video_scripts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2.4 补充视频剧本扩展字段
CALL add_column_if_not_exists('video_scripts', 'script_format', "VARCHAR(30) NOT NULL DEFAULT 'STORYBOARD' AFTER mode");
CALL add_column_if_not_exists('video_scripts', 'outline', 'TEXT AFTER script_setting');
CALL add_column_if_not_exists('video_scripts', 'hooks_json', 'TEXT AFTER outline');
CALL add_column_if_not_exists('video_scripts', 'prologue', 'TEXT AFTER hooks_json');
CALL add_column_if_not_exists('video_scripts', 'episode_count', 'INT DEFAULT 10 AFTER scene_count');
CALL add_column_if_not_exists('video_scripts', 'current_episode', 'INT DEFAULT 0 AFTER episode_count');
CALL add_column_if_not_exists('video_scripts', 'current_retry_count', 'INT DEFAULT 0 AFTER current_episode');
CALL add_column_if_not_exists('video_scripts', 'max_retry_per_episode', 'INT DEFAULT 3 AFTER current_retry_count');
CALL add_column_if_not_exists('video_scripts', 'min_pass_score', 'INT DEFAULT 7 AFTER max_retry_per_episode');
CALL add_column_if_not_exists('video_scripts', 'enable_outline_update', 'TINYINT(1) DEFAULT 1 AFTER min_pass_score');

-- 2.5 补充日志表字段
CALL add_column_if_not_exists('video_script_logs', 'episode_number', 'INT NULL AFTER script_id');

-- 尝试创建索引（如果不存在）
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS 
                   WHERE TABLE_SCHEMA = DATABASE() 
                   AND TABLE_NAME = 'video_script_logs' 
                   AND INDEX_NAME = 'idx_script_episode_log');
SET @sql = IF(@idx_exists = 0, 
              'CREATE INDEX idx_script_episode_log ON video_script_logs(script_id, episode_number)', 
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 清理存储过程
DROP PROCEDURE IF EXISTS add_column_if_not_exists;

-- ============================================
-- 完成
-- ============================================
SELECT '迁移完成！短篇小说和视频剧本相关表已创建/更新。' AS message;
