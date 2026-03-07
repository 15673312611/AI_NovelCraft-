-- ============================================
-- Neo4j迁移到MySQL - 图谱相关表定�?
-- ============================================

-- 1. 角色状态表
CREATE TABLE IF NOT EXISTS graph_character_state (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL,
    character_name VARCHAR(100) NOT NULL,
    location VARCHAR(255),
    realm VARCHAR(100),
    alive BOOLEAN DEFAULT TRUE,
    affiliation VARCHAR(255),
    social_status VARCHAR(100),
    backers TEXT COMMENT 'JSON数组',
    tags TEXT COMMENT 'JSON数组',
    secrets TEXT COMMENT 'JSON数组',
    key_items TEXT COMMENT 'JSON数组',
    known_by TEXT COMMENT 'JSON数组',
    inventory TEXT COMMENT 'JSON数组',
    character_info TEXT,
    last_updated_chapter INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_novel_character (novel_id, character_name),
    INDEX idx_novel_id (novel_id),
    INDEX idx_last_updated (novel_id, last_updated_chapter)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色状态表';

-- 2. 角色状态历史快照表
CREATE TABLE IF NOT EXISTS graph_character_state_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL,
    character_name VARCHAR(100) NOT NULL,
    location VARCHAR(255),
    realm VARCHAR(100),
    alive BOOLEAN,
    inventory TEXT,
    character_info TEXT,
    chapter_number INT NOT NULL COMMENT '快照对应的章�?,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_novel_char_chapter (novel_id, character_name, chapter_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色状态历史快照表';

-- 3. 关系状态表
CREATE TABLE IF NOT EXISTS graph_relationship_state (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL,
    character_a VARCHAR(100) NOT NULL COMMENT '按字典序较小的角色名',
    character_b VARCHAR(100) NOT NULL COMMENT '按字典序较大的角色名',
    type VARCHAR(50),
    strength DOUBLE DEFAULT 0.5,
    description TEXT,
    public_status VARCHAR(50),
    last_updated_chapter INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_novel_relation (novel_id, character_a, character_b),
    INDEX idx_novel_id (novel_id),
    INDEX idx_strength (novel_id, strength)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关系状态表';

-- 4. 关系状态历史表
CREATE TABLE IF NOT EXISTS graph_relationship_state_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL,
    character_a VARCHAR(100) NOT NULL,
    character_b VARCHAR(100) NOT NULL,
    type VARCHAR(50),
    strength DOUBLE,
    description TEXT,
    public_status VARCHAR(50),
    chapter_number INT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_novel_rel_chapter (novel_id, character_a, character_b, chapter_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关系状态历史表';

-- 5. 开放任务表
CREATE TABLE IF NOT EXISTS graph_open_quest (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL,
    quest_id VARCHAR(100) NOT NULL COMMENT '业务ID',
    description TEXT,
    status VARCHAR(20) DEFAULT 'OPEN',
    introduced_chapter INT,
    due_by_chapter INT,
    resolved_chapter INT,
    last_updated_chapter INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_novel_quest (novel_id, quest_id),
    INDEX idx_novel_status (novel_id, status),
    INDEX idx_due_chapter (novel_id, due_by_chapter)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开放任务表';

-- 6. 开放任务历史表
CREATE TABLE IF NOT EXISTS graph_open_quest_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL,
    quest_id VARCHAR(100) NOT NULL,
    description TEXT,
    status VARCHAR(20),
    chapter_number INT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_novel_quest_chapter (novel_id, quest_id, chapter_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开放任务历史表';

-- 7. 事件�?
CREATE TABLE IF NOT EXISTS graph_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL,
    event_id VARCHAR(100) NOT NULL COMMENT '业务ID',
    chapter_number INT,
    summary TEXT,
    description TEXT,
    location VARCHAR(255),
    realm VARCHAR(100),
    emotional_tone VARCHAR(50),
    tags TEXT COMMENT 'JSON数组',
    importance DOUBLE DEFAULT 0.5,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_novel_event (novel_id, event_id),
    INDEX idx_novel_chapter (novel_id, chapter_number),
    INDEX idx_importance (novel_id, importance)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事件�?;

-- 8. 事件参与者表（多对多�?
CREATE TABLE IF NOT EXISTS graph_event_participant (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id BIGINT NOT NULL,
    character_name VARCHAR(100) NOT NULL,
    INDEX idx_event_id (event_id),
    INDEX idx_character (character_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事件参与者表';

-- 9. 事件因果关系�?
CREATE TABLE IF NOT EXISTS graph_event_causal (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL,
    from_event_id VARCHAR(100) NOT NULL,
    to_event_id VARCHAR(100) NOT NULL,
    relation_type VARCHAR(50) COMMENT 'CAUSES/TRIGGERS/TRIGGERED_BY',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_from_event (novel_id, from_event_id),
    INDEX idx_to_event (novel_id, to_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事件因果关系�?;

-- 10. 伏笔�?
CREATE TABLE IF NOT EXISTS graph_foreshadowing (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL,
    foreshadow_id VARCHAR(100) NOT NULL COMMENT '业务ID',
    content TEXT,
    importance VARCHAR(20) DEFAULT 'medium',
    status VARCHAR(20) DEFAULT 'PLANTED',
    introduced_chapter INT,
    planned_reveal_chapter INT,
    resolved_chapter INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_novel_foreshadow (novel_id, foreshadow_id),
    INDEX idx_novel_status (novel_id, status),
    INDEX idx_reveal_chapter (novel_id, planned_reveal_chapter)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='伏笔�?;

-- 11. 情节线表
CREATE TABLE IF NOT EXISTS graph_plotline (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL,
    plotline_id VARCHAR(100) NOT NULL COMMENT '业务ID',
    name VARCHAR(255),
    priority DOUBLE DEFAULT 0.5,
    last_touched_chapter INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_novel_plotline (novel_id, plotline_id),
    INDEX idx_novel_priority (novel_id, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='情节线表';

-- 12. 情节线事件关联表
CREATE TABLE IF NOT EXISTS graph_plotline_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plotline_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    UNIQUE KEY uk_plotline_event (plotline_id, event_id),
    INDEX idx_plotline_id (plotline_id),
    INDEX idx_event_id (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='情节线事件关联表';

-- 13. 世界规则�?
CREATE TABLE IF NOT EXISTS graph_world_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL,
    rule_id VARCHAR(100) NOT NULL COMMENT '业务ID',
    name VARCHAR(255),
    content TEXT,
    constraint_text TEXT,
    category VARCHAR(50) DEFAULT 'general',
    scope VARCHAR(50) DEFAULT 'global',
    importance DOUBLE DEFAULT 0.5,
    introduced_at INT,
    applicable_chapter INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_novel_rule (novel_id, rule_id),
    INDEX idx_novel_category (novel_id, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='世界规则�?;

-- 14. 叙事节奏�?
CREATE TABLE IF NOT EXISTS graph_narrative_beat (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL,
    beat_id VARCHAR(100),
    chapter_number INT NOT NULL,
    beat_type VARCHAR(50),
    focus VARCHAR(255),
    sentiment VARCHAR(50),
    tension DOUBLE DEFAULT 0.5,
    pace_score DOUBLE DEFAULT 0.5,
    viewpoint VARCHAR(100),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_novel_chapter (novel_id, chapter_number),
    INDEX idx_novel_id (novel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='叙事节奏�?;

-- 15. 冲突弧线�?
CREATE TABLE IF NOT EXISTS graph_conflict_arc (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL,
    arc_id VARCHAR(100) NOT NULL COMMENT '业务ID',
    name VARCHAR(255),
    stage VARCHAR(50),
    urgency DOUBLE DEFAULT 0.5,
    next_action TEXT,
    protagonist VARCHAR(100),
    antagonist VARCHAR(100),
    trend VARCHAR(50),
    last_updated_chapter INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_novel_arc (novel_id, arc_id),
    INDEX idx_novel_stage (novel_id, stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='冲突弧线�?;

-- 16. 人物成长弧线�?
CREATE TABLE IF NOT EXISTS graph_character_arc (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL,
    arc_id VARCHAR(100) NOT NULL COMMENT '业务ID',
    character_name VARCHAR(100),
    arc_name VARCHAR(255),
    pending_beat TEXT,
    next_goal TEXT,
    priority DOUBLE DEFAULT 0.5,
    progress INT DEFAULT 0,
    total_beats INT DEFAULT 0,
    last_updated_chapter INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_novel_arc (novel_id, arc_id),
    INDEX idx_novel_character (novel_id, character_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人物成长弧线�?;

-- 17. 视角使用�?
CREATE TABLE IF NOT EXISTS graph_perspective_usage (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL,
    perspective_id VARCHAR(100),
    chapter_number INT NOT NULL,
    character_name VARCHAR(100),
    mode VARCHAR(50) DEFAULT '第三人称',
    tone VARCHAR(50),
    purpose TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_novel_chapter (novel_id, chapter_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视角使用�?;

-- 18. 章节摘要信号�?
CREATE TABLE IF NOT EXISTS graph_summary_signal (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL,
    chapter_number INT NOT NULL,
    signal_key VARCHAR(100) NOT NULL,
    signal_value TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_novel_chapter_key (novel_id, chapter_number, signal_key),
    INDEX idx_novel_chapter (novel_id, chapter_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='章节摘要信号�?;

-- 19. 角色档案�?
CREATE TABLE IF NOT EXISTS graph_character_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL,
    profile_id VARCHAR(100) NOT NULL,
    chapter_number INT,
    properties TEXT COMMENT 'JSON存储所有属�?,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_novel_profile (novel_id, profile_id),
    INDEX idx_novel_chapter (novel_id, chapter_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色档案�?;
