-- 模板循环引擎：小说模板进度表
-- 用于跟踪每个小说在五步循环中的当前状态

CREATE TABLE IF NOT EXISTS novel_template_progress (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    novel_id BIGINT NOT NULL UNIQUE COMMENT '关联的小说ID（唯一）',
    
    -- 开关控制
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用模板引擎',
    
    -- 当前状态
    current_stage VARCHAR(50) NOT NULL DEFAULT 'MOTIVATION' COMMENT '当前阶段: MOTIVATION/BONUS/CONFRONTATION/RESPONSE/EARNING',
    loop_number INT NOT NULL DEFAULT 1 COMMENT '当前循环次数（第几轮）',
    stage_start_chapter INT NOT NULL DEFAULT 1 COMMENT '当前阶段的起始章节号',
    
    -- 各阶段分析内容（AI分析结果）
    motivation_analysis TEXT COMMENT '动机阶段分析内容',
    bonus_analysis TEXT COMMENT '金手指阶段分析内容',
    confrontation_analysis TEXT COMMENT '装逼/冲突阶段分析内容',
    response_analysis TEXT COMMENT '反馈阶段分析内容',
    earning_analysis TEXT COMMENT '收获阶段分析内容',
    
    -- 模板配置
    template_type VARCHAR(50) DEFAULT 'GENERAL' COMMENT '模板类型: XUANHUAN/URBAN/SYSTEM/REBIRTH/GENERAL',
    start_chapter INT NOT NULL DEFAULT 1 COMMENT '模板引擎启动章节（从第几章开始应用）',
    last_updated_chapter INT NOT NULL DEFAULT 0 COMMENT '最后更新的章节号',
    
    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    -- 索引
    INDEX idx_novel_id (novel_id),
    INDEX idx_enabled (enabled),
    INDEX idx_current_stage (current_stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小说模板循环进度表';

