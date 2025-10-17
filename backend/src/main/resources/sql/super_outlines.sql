-- 超级大纲表
CREATE TABLE IF NOT EXISTS super_outlines (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    novel_id BIGINT NOT NULL COMMENT '关联小说ID',
    original_idea TEXT NOT NULL COMMENT '用户输入的原始构思',
    title VARCHAR(500) COMMENT '超级大纲标题',
    content LONGTEXT COMMENT '超级大纲内容（JSON格式）',
    core_theme TEXT COMMENT '核心主题',
    background TEXT COMMENT '故事背景',
    main_characters TEXT COMMENT '主要角色',
    plot_structure TEXT COMMENT '情节结构',
    world_building TEXT COMMENT '世界观设定',
    target_chapters INT COMMENT '预期目标章数',
    target_words INT COMMENT '预期总字数',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '超级大纲状态：DRAFT(草稿), OPTIMIZING(优化中), CONFIRMED(已确认)',
    optimization_count INT DEFAULT 0 COMMENT 'AI优化次数',
    user_rating INT COMMENT '用户评分（1-5星）',
    user_feedback TEXT COMMENT '用户反馈意见',
    last_optimization_suggestion TEXT COMMENT '最后一次优化建议',
    volumes_generated BOOLEAN DEFAULT FALSE COMMENT '是否已生成卷规划',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    last_optimized_at DATETIME COMMENT '最后AI优化时间',
    
    INDEX idx_novel_id (novel_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    
    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='超级大纲表';