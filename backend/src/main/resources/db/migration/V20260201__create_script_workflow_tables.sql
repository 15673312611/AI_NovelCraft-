-- 短视频剧本工作流：主表 + 日志表

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
