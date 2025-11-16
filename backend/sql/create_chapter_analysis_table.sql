-- 章节拆解分析表
CREATE TABLE IF NOT EXISTS chapter_analysis (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    novel_id BIGINT NOT NULL COMMENT '小说ID',
    analysis_type VARCHAR(50) NOT NULL COMMENT '分析类型：golden_three(黄金三章), main_plot(主线剧情), sub_plot(支线剧情), theme(主题分析), character(角色分析), worldbuilding(世界设定), writing_style(写作风格与技巧)',
    start_chapter INT NOT NULL COMMENT '开始章节号',
    end_chapter INT NOT NULL COMMENT '结束章节号',
    analysis_content TEXT COMMENT '分析内容（Markdown格式）',
    word_count INT DEFAULT 0 COMMENT '分析内容字数',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_novel_id (novel_id),
    INDEX idx_analysis_type (analysis_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='章节拆解分析表';
