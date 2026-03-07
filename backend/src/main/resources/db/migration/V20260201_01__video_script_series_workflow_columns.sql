-- 升级剧本工厂为“多集连续剧本工作流”

-- 1) video_scripts 增加：大纲/看点/导语/集数/循环控制等字段
ALTER TABLE video_scripts
  ADD COLUMN outline TEXT AFTER script_setting,
  ADD COLUMN hooks_json TEXT AFTER outline,
  ADD COLUMN prologue TEXT AFTER hooks_json,
  ADD COLUMN episode_count INT DEFAULT 10 AFTER scene_count,
  ADD COLUMN current_episode INT DEFAULT 0 AFTER episode_count,
  ADD COLUMN current_retry_count INT DEFAULT 0 AFTER current_episode,
  ADD COLUMN max_retry_per_episode INT DEFAULT 3 AFTER current_retry_count,
  ADD COLUMN min_pass_score INT DEFAULT 7 AFTER max_retry_per_episode,
  ADD COLUMN enable_outline_update TINYINT(1) DEFAULT 1 AFTER min_pass_score;

-- 2) 新增每集产物表（类似 short_novel_chapters）
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

-- 3) 日志增加 episode_number，便于筛选
ALTER TABLE video_script_logs
  ADD COLUMN episode_number INT NULL AFTER script_id;

CREATE INDEX idx_script_episode_log ON video_script_logs(script_id, episode_number);
