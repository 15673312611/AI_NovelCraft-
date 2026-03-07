-- 短篇小说主表
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

-- 短篇小说章节表
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

-- 工作流日志表
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
