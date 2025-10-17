-- 长篇小说记忆管理数据表

-- 1. 角色档案表
CREATE TABLE IF NOT EXISTS novel_character_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    novel_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL COMMENT '角色姓名',
    first_appearance INT NOT NULL COMMENT '首次出现章节',
    last_appearance INT NOT NULL COMMENT '最后出现章节',
    appearance_count INT DEFAULT 1 COMMENT '出现次数',
    status ENUM('ACTIVE', 'DEAD', 'INJURED', 'ABSENT') DEFAULT 'ACTIVE' COMMENT '角色状态',
    status_change_chapter INT NULL COMMENT '状态改变章节',
    personality_traits JSON COMMENT '性格特征',
    key_events JSON COMMENT '关键事件',
    relationships JSON COMMENT '人际关系',
    actions_history JSON COMMENT '行为历史',
    importance_score INT DEFAULT 50 COMMENT '重要性评分',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_novel_character (novel_id, name),
    INDEX idx_appearance (last_appearance),
    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE
) COMMENT '角色档案表';

-- 2. 大事年表
CREATE TABLE IF NOT EXISTS novel_chronicle (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    novel_id BIGINT NOT NULL,
    chapter_number INT NOT NULL COMMENT '章节号',
    events JSON NOT NULL COMMENT '事件列表',
    timeline_info VARCHAR(200) COMMENT '时间线信息',
    event_type ENUM('DEATH', 'MARRIAGE', 'BREAKTHROUGH', 'BATTLE', 'DISCOVERY', 'TRAVEL', 'DECISION') COMMENT '事件类型',
    importance_level TINYINT DEFAULT 5 COMMENT '重要程度1-10',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_novel_chapter (novel_id, chapter_number),
    INDEX idx_event_type (event_type),
    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE
) COMMENT '大事年表';

-- 3. 伏笔追踪表
CREATE TABLE IF NOT EXISTS novel_foreshadowing (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    novel_id BIGINT NOT NULL,
    content TEXT NOT NULL COMMENT '伏笔内容',
    planted_chapter INT NOT NULL COMMENT '埋设章节',
    resolved_chapter INT NULL COMMENT '回收章节',
    status ENUM('ACTIVE', 'RESOLVED', 'ABANDONED') DEFAULT 'ACTIVE' COMMENT '伏笔状态',
    type ENUM('DEATH', 'ROMANCE', 'CONFLICT', 'MYSTERY', 'POWER', 'OTHER') DEFAULT 'OTHER' COMMENT '伏笔类型',
    priority TINYINT DEFAULT 5 COMMENT '优先级1-10',
    context_info TEXT COMMENT '上下文信息',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    resolved_time DATETIME NULL,
    INDEX idx_novel_planted (novel_id, planted_chapter),
    INDEX idx_status_type (status, type),
    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE
) COMMENT '伏笔追踪表';

-- 4. 世界观词典
CREATE TABLE IF NOT EXISTS novel_world_dictionary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    novel_id BIGINT NOT NULL,
    term VARCHAR(100) NOT NULL COMMENT '词条',
    type ENUM('GEOGRAPHY', 'POWER_SYSTEM', 'ORGANIZATION', 'ITEM', 'CONCEPT') NOT NULL COMMENT '词条类型',
    first_mention INT NOT NULL COMMENT '首次出现章节',
    description TEXT COMMENT '描述信息',
    context_info TEXT COMMENT '上下文',
    usage_count INT DEFAULT 1 COMMENT '使用次数',
    is_important BOOLEAN DEFAULT FALSE COMMENT '是否重要设定',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_novel_term (novel_id, term),
    INDEX idx_type_important (type, is_important),
    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE
) COMMENT '世界观词典';

-- 5. 记忆库版本管理
CREATE TABLE IF NOT EXISTS novel_memory_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    novel_id BIGINT NOT NULL,
    version_number INT NOT NULL COMMENT '版本号',
    last_updated_chapter INT NOT NULL COMMENT '最后更新章节',
    character_count INT DEFAULT 0 COMMENT '角色数量',
    event_count INT DEFAULT 0 COMMENT '事件数量',
    foreshadowing_count INT DEFAULT 0 COMMENT '伏笔数量',
    term_count INT DEFAULT 0 COMMENT '词条数量',
    conflict_warnings JSON COMMENT '冲突警告',
    memory_snapshot JSON COMMENT '记忆快照',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_novel_version (novel_id, version_number),
    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE
) COMMENT '记忆库版本管理';

-- 6. AI生成任务表
CREATE TABLE IF NOT EXISTS ai_generation_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_type ENUM('OUTLINE_FRAMEWORK', 'CHAPTER_PLANS', 'OUTLINE_ELEMENTS', 'CHARACTER_PROFILES', 'WORLD_BUILDING') NOT NULL COMMENT '任务类型',
    novel_id BIGINT NOT NULL,
    volume_id BIGINT NULL,
    chapter_number INT NULL,
    status ENUM('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED') DEFAULT 'PENDING' COMMENT '任务状态',
    input_data JSON COMMENT '输入数据',
    output_data JSON COMMENT '输出数据',
    error_message TEXT COMMENT '错误信息',
    processing_rounds INT DEFAULT 1 COMMENT '处理轮次',
    total_rounds INT DEFAULT 1 COMMENT '总轮次',
    prompt_used TEXT COMMENT '使用的提示词',
    ai_response_raw TEXT COMMENT '原始AI响应',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_time DATETIME NULL,
    INDEX idx_status_type (status, task_type),
    INDEX idx_novel_task (novel_id, task_type),
    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE
) COMMENT 'AI生成任务表';

-- 添加索引优化查询性能
ALTER TABLE novel_character_profiles ADD INDEX idx_status_appearance (status, last_appearance);
ALTER TABLE novel_chronicle ADD INDEX idx_importance_chapter (importance_level, chapter_number);
ALTER TABLE novel_foreshadowing ADD INDEX idx_priority_status (priority, status);
ALTER TABLE novel_world_dictionary ADD INDEX idx_usage_important (usage_count, is_important);