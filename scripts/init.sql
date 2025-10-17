-- 小说创作系统数据库初始化脚本（合并版本）
-- 创建时间: 2024-01-01
-- 更新时间: 2024-12-20
-- 版本: 2.0.0
-- 数据库: ai_novel

-- 设置SQL模式和字符集
SET SQL_MODE = 'NO_AUTO_VALUE_ON_ZERO';
SET AUTOCOMMIT = 0;
START TRANSACTION;
SET time_zone = "+00:00";

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS ai_novel
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE ai_novel;

-- 启用审计功能（需要SUPER权限，如果权限不足可以注释掉这行）
-- SET GLOBAL log_bin_trust_function_creators = 1;

-- 禁用外键检查（在数据插入时避免顺序问题）
SET FOREIGN_KEY_CHECKS = 0;

-- 创建用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    status ENUM('ACTIVE', 'INACTIVE', 'BANNED') NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建角色表
CREATE TABLE IF NOT EXISTS roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    permissions JSON,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建用户角色关联表
CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建小说表
CREATE TABLE IF NOT EXISTS novels (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    genre VARCHAR(100),
    status ENUM('DRAFT', 'WRITING', 'REVIEWING', 'COMPLETED') NOT NULL DEFAULT 'DRAFT',
    word_count INT DEFAULT 0,
    chapter_count INT DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_title (title),
    INDEX idx_genre (genre),
    INDEX idx_status (status),
    INDEX idx_created_by (created_by),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建章节表
CREATE TABLE IF NOT EXISTS chapters (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    order_num INT NOT NULL,
    status ENUM('DRAFT', 'WRITING', 'REVIEWING', 'COMPLETED') NOT NULL DEFAULT 'DRAFT',
    word_count INT DEFAULT 0,
    novel_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE,
    INDEX idx_novel_id (novel_id),
    INDEX idx_order_num (order_num),
    INDEX idx_status (status),
    INDEX idx_title (title)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建小说人物角色表
CREATE TABLE IF NOT EXISTS characters (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    personality TEXT,
    background TEXT,
    relationships TEXT,
    role VARCHAR(50),
    appearance TEXT,
    abilities TEXT,
    goals TEXT,
    conflicts TEXT,
    novel_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE,
    INDEX idx_novel_id (novel_id),
    INDEX idx_name (name),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建世界观表
CREATE TABLE IF NOT EXISTS world_views (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    background TEXT,
    rules TEXT,
    timeline TEXT,
    locations JSON,
    magic_system TEXT,
    technology TEXT,
    social_structure TEXT,
    novel_id BIGINT NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE,
    INDEX idx_novel_id (novel_id),
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建提示词表
CREATE TABLE IF NOT EXISTS prompts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(100),
    style VARCHAR(100),
    description TEXT,
    tags JSON,
    usage_count INT DEFAULT 0,
    effectiveness_score DECIMAL(3,2) DEFAULT 0.00,
    examples TEXT,
    author VARCHAR(50),
    is_public BOOLEAN DEFAULT TRUE,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_category (category),
    INDEX idx_style (style),
    INDEX idx_created_by (created_by),
    INDEX idx_is_public (is_public)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建AI任务表
CREATE TABLE IF NOT EXISTS ai_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    type ENUM('WRITE_CHAPTER', 'ANALYZE_QUALITY', 'OPTIMIZE_CONTENT', 'BUILD_WORLDVIEW', 'ANALYZE_PROGRESS', 'MANAGE_FORESHADOWING', 'PLAN_PLOT_POINTS', 'EVALUATE_STAGE') NOT NULL,
    status ENUM('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED') NOT NULL DEFAULT 'PENDING',
    input TEXT,
    output TEXT,
    error TEXT,
    progress_percentage INT DEFAULT 0,
    estimated_completion TIMESTAMP NULL,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    parameters TEXT,
    cost_estimate DECIMAL(10,4),
    actual_cost DECIMAL(10,4),
    created_by BIGINT,
    novel_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE SET NULL,
    INDEX idx_type (type),
    INDEX idx_status (status),
    INDEX idx_created_by (created_by),
    INDEX idx_novel_id (novel_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建进度表
CREATE TABLE IF NOT EXISTS progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    novel_id BIGINT NOT NULL UNIQUE,
    current_chapter INT DEFAULT 1,
    total_chapters INT DEFAULT 0,
    completion_rate DECIMAL(5,2) DEFAULT 0.00,
    current_stage VARCHAR(50) DEFAULT '开篇',
    word_count INT DEFAULT 0,
    target_word_count INT DEFAULT 0,
    estimated_completion TIMESTAMP NULL,
    daily_word_goal INT DEFAULT 1000,
    last_writing_date TIMESTAMP NULL,
    writing_streak INT DEFAULT 0,
    total_writing_time BIGINT DEFAULT 0,
    average_words_per_hour DECIMAL(8,2) DEFAULT 0.00,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE,
    INDEX idx_novel_id (novel_id),
    INDEX idx_current_stage (current_stage),
    INDEX idx_completion_rate (completion_rate)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建伏笔表
CREATE TABLE IF NOT EXISTS foreshadowing (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content TEXT NOT NULL,
    type ENUM('CHARACTER_DEVELOPMENT', 'PLOT_TWIST', 'WORLD_BUILDING', 'RELATIONSHIP', 'CONFLICT', 'MYSTERY', 'EMOTIONAL', 'THEMATIC') NOT NULL,
    planned_chapter INT,
    status ENUM('PLANNED', 'PLANTED', 'DEVELOPING', 'COMPLETED') NOT NULL DEFAULT 'PLANNED',
    description TEXT,
    importance_level INT DEFAULT 1,
    related_elements TEXT,
    planted_chapter INT,
    completed_chapter INT,
    notes TEXT,
    novel_id BIGINT NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_novel_id (novel_id),
    INDEX idx_type (type),
    INDEX idx_status (status),
    INDEX idx_importance_level (importance_level),
    INDEX idx_planned_chapter (planned_chapter)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建剧情节点表
CREATE TABLE IF NOT EXISTS plot_points (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    type ENUM('INCITING_INCIDENT', 'FIRST_PLOT_POINT', 'FIRST_PINCH_POINT', 'MIDPOINT', 'SECOND_PINCH_POINT', 'SECOND_PLOT_POINT', 'CLIMAX', 'RESOLUTION', 'CHARACTER_ARC', 'RELATIONSHIP_DEVELOPMENT', 'WORLD_EXPANSION', 'THEME_REVELATION') NOT NULL,
    planned_chapter INT,
    importance ENUM('CRITICAL', 'HIGH', 'MEDIUM', 'LOW') NOT NULL DEFAULT 'MEDIUM',
    description TEXT,
    requirements TEXT,
    impact TEXT,
    consequences TEXT,
    is_completed BOOLEAN DEFAULT FALSE,
    completed_chapter INT,
    completion_date TIMESTAMP NULL,
    notes TEXT,
    novel_id BIGINT NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_novel_id (novel_id),
    INDEX idx_type (type),
    INDEX idx_importance (importance),
    INDEX idx_is_completed (is_completed),
    INDEX idx_planned_chapter (planned_chapter)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建参考小说表
CREATE TABLE IF NOT EXISTS reference_novels (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    author VARCHAR(100),
    genre VARCHAR(100),
    file_path VARCHAR(500),
    file_size BIGINT,
    total_segments INT DEFAULT 0,
    style TEXT,
    summary TEXT,
    word_count INT DEFAULT 0,
    analysis_status ENUM('UPLOADED', 'PROCESSING', 'ANALYZED', 'FAILED') NOT NULL DEFAULT 'UPLOADED',
    analysis_progress INT DEFAULT 0,
    analysis_started_at TIMESTAMP NULL,
    analysis_completed_at TIMESTAMP NULL,
    analysis_result TEXT,
    error_message TEXT,
    uploaded_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (uploaded_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_title (title),
    INDEX idx_author (author),
    INDEX idx_genre (genre),
    INDEX idx_analysis_status (analysis_status),
    INDEX idx_uploaded_by (uploaded_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建写作技巧表
CREATE TABLE IF NOT EXISTS writing_techniques (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    category ENUM('NARRATION', 'DIALOGUE', 'DESCRIPTION', 'EMOTION', 'ACTION', 'ATMOSPHERE', 'CHARACTER', 'PLOT', 'PACING', 'SUSPENSE', 'HUMOR', 'METAPHOR') NOT NULL,
    examples TEXT,
    effectiveness_score DECIMAL(3,2) DEFAULT 0.00,
    usage_count INT DEFAULT 0,
    difficulty_level INT DEFAULT 1,
    tips TEXT,
    common_mistakes TEXT,
    related_techniques TEXT,
    is_public BOOLEAN DEFAULT TRUE,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_name (name),
    INDEX idx_category (category),
    INDEX idx_difficulty_level (difficulty_level),
    INDEX idx_is_public (is_public),
    INDEX idx_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 检查并添加permissions列（如果不存在）
SET @column_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'ai_novel'
    AND TABLE_NAME = 'roles'
    AND COLUMN_NAME = 'permissions'
);

SET @sql = IF(@column_exists = 0,
    'ALTER TABLE roles ADD COLUMN permissions JSON AFTER description',
    'SELECT "permissions column already exists" as status'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 插入初始角色数据
INSERT INTO roles (name, description, permissions) VALUES
('ADMIN', '系统管理员', '["*"]'),
('AUTHOR', '作者', '["novel:read", "novel:write", "chapter:read", "chapter:write", "character:read", "character:write", "worldview:read", "worldview:write"]'),
('EDITOR', '编辑', '["novel:read", "novel:review", "chapter:read", "chapter:review", "character:read", "character:review"]'),
('READER', '读者', '["novel:read", "chapter:read"]')
ON DUPLICATE KEY UPDATE
    description = VALUES(description),
    permissions = VALUES(permissions);

-- 插入初始用户数据（密码为123456的BCrypt加密）
INSERT INTO users (username, email, password, status) VALUES
('admin', 'admin@novel.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDa', 'ACTIVE'),
('author1', 'author1@novel.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDa', 'ACTIVE'),
('editor1', 'editor1@novel.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDa', 'ACTIVE')
ON DUPLICATE KEY UPDATE
    email = VALUES(email),
    password = VALUES(password),
    status = VALUES(status);

-- 分配角色（使用子查询获取正确的ID）
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE (u.username = 'admin' AND r.name = 'ADMIN')
   OR (u.username = 'author1' AND r.name = 'AUTHOR')
   OR (u.username = 'editor1' AND r.name = 'EDITOR')
ON DUPLICATE KEY UPDATE user_id = VALUES(user_id);

-- 检查并添加writing_techniques表的缺失列
SET @difficulty_level_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'ai_novel'
    AND TABLE_NAME = 'writing_techniques'
    AND COLUMN_NAME = 'difficulty_level'
);

SET @effectiveness_score_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'ai_novel'
    AND TABLE_NAME = 'writing_techniques'
    AND COLUMN_NAME = 'effectiveness_score'
);

SET @usage_count_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'ai_novel'
    AND TABLE_NAME = 'writing_techniques'
    AND COLUMN_NAME = 'usage_count'
);

SET @tips_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'ai_novel'
    AND TABLE_NAME = 'writing_techniques'
    AND COLUMN_NAME = 'tips'
);

SET @common_mistakes_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'ai_novel'
    AND TABLE_NAME = 'writing_techniques'
    AND COLUMN_NAME = 'common_mistakes'
);

SET @related_techniques_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'ai_novel'
    AND TABLE_NAME = 'writing_techniques'
    AND COLUMN_NAME = 'related_techniques'
);

SET @is_public_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'ai_novel'
    AND TABLE_NAME = 'writing_techniques'
    AND COLUMN_NAME = 'is_public'
);

-- 添加缺失的列
SET @sql = CASE
    WHEN @difficulty_level_exists = 0 THEN 'ALTER TABLE writing_techniques ADD COLUMN difficulty_level INT DEFAULT 1 AFTER usage_count;'
    ELSE 'SELECT "difficulty_level column already exists" as status;'
END;
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = CASE
    WHEN @effectiveness_score_exists = 0 THEN 'ALTER TABLE writing_techniques ADD COLUMN effectiveness_score DECIMAL(3,2) DEFAULT 0.00 AFTER examples;'
    ELSE 'SELECT "effectiveness_score column already exists" as status;'
END;
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = CASE
    WHEN @usage_count_exists = 0 THEN 'ALTER TABLE writing_techniques ADD COLUMN usage_count INT DEFAULT 0 AFTER effectiveness_score;'
    ELSE 'SELECT "usage_count column already exists" as status;'
END;
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = CASE
    WHEN @tips_exists = 0 THEN 'ALTER TABLE writing_techniques ADD COLUMN tips TEXT AFTER difficulty_level;'
    ELSE 'SELECT "tips column already exists" as status;'
END;
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = CASE
    WHEN @common_mistakes_exists = 0 THEN 'ALTER TABLE writing_techniques ADD COLUMN common_mistakes TEXT AFTER tips;'
    ELSE 'SELECT "common_mistakes column already exists" as status;'
END;
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = CASE
    WHEN @related_techniques_exists = 0 THEN 'ALTER TABLE writing_techniques ADD COLUMN related_techniques TEXT AFTER common_mistakes;'
    ELSE 'SELECT "related_techniques column already exists" as status;'
END;
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = CASE
    WHEN @is_public_exists = 0 THEN 'ALTER TABLE writing_techniques ADD COLUMN is_public BOOLEAN DEFAULT TRUE AFTER related_techniques;'
    ELSE 'SELECT "is_public column already exists" as status;'
END;
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 插入初始写作技巧数据
INSERT INTO writing_techniques (name, description, category, examples, difficulty_level, tips, common_mistakes) VALUES
('细腻的心理描写', '通过内心独白、心理活动等方式深入刻画角色内心世界', 'CHARACTER', '他站在窗前，内心挣扎着是否要推开这扇门。恐惧和好奇在他心中交织，就像两只野兽在搏斗。', 3, '结合角色的背景和性格特点，使用恰当的比喻和象征', '过度使用心理描写导致节奏拖沓'),
('生动的环境描写', '通过感官细节营造身临其境的氛围感', 'DESCRIPTION', '夕阳西下，金色的光芒透过树叶洒在石板路上，斑驳的光影随着微风轻轻摇曳。空气中弥漫着桂花的香气，远处传来悠扬的钟声。', 2, '调动多种感官，注意细节的选择和顺序', '堆砌华丽辞藻，缺乏重点'),
('自然的对话推进', '通过对话自然地推动情节发展，展现角色关系', 'DIALOGUE', '"你确定要这么做吗？"她轻声问道，眼中闪过一丝担忧。"别无选择。"他握紧拳头，声音坚定。', 2, '让对话符合角色身份，注意语气和用词', '对话过于直白，缺乏潜台词'),
('紧凑的节奏控制', '通过短句、快节奏的描写营造紧张氛围', 'PACING', '心跳加速。脚步声越来越近。他屏住呼吸，躲在门后。门把手转动的声音在寂静中格外清晰。', 4, '长短句结合，注意停顿和节奏变化', '一味追求快节奏，缺乏变化'),
('巧妙的伏笔设置', '在故事中埋下线索，为后续发展做铺垫', 'PLOT', '老人颤抖着将钥匙交给年轻人，眼中闪过一丝复杂的神色。"记住，有些门一旦打开，就再也关不上了。"', 5, '伏笔要自然，不能过于明显或突兀', '伏笔过于明显，失去悬念效果')
ON DUPLICATE KEY UPDATE
    description = VALUES(description),
    category = VALUES(category),
    examples = VALUES(examples),
    difficulty_level = VALUES(difficulty_level),
    tips = VALUES(tips),
    common_mistakes = VALUES(common_mistakes);

-- 检查并添加prompts表的缺失列
SET @tags_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'ai_novel'
    AND TABLE_NAME = 'prompts'
    AND COLUMN_NAME = 'tags'
);

SET @usage_count_prompts_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'ai_novel'
    AND TABLE_NAME = 'prompts'
    AND COLUMN_NAME = 'usage_count'
);

SET @effectiveness_score_prompts_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'ai_novel'
    AND TABLE_NAME = 'prompts'
    AND COLUMN_NAME = 'effectiveness_score'
);

SET @examples_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'ai_novel'
    AND TABLE_NAME = 'prompts'
    AND COLUMN_NAME = 'examples'
);

SET @author_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'ai_novel'
    AND TABLE_NAME = 'prompts'
    AND COLUMN_NAME = 'author'
);

SET @is_public_prompts_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'ai_novel'
    AND TABLE_NAME = 'prompts'
    AND COLUMN_NAME = 'is_public'
);

-- 添加缺失的列
SET @sql = CASE
    WHEN @tags_exists = 0 THEN 'ALTER TABLE prompts ADD COLUMN tags JSON AFTER description;'
    ELSE 'SELECT "tags column already exists" as status;'
END;
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = CASE
    WHEN @usage_count_prompts_exists = 0 THEN 'ALTER TABLE prompts ADD COLUMN usage_count INT DEFAULT 0 AFTER tags;'
    ELSE 'SELECT "usage_count column already exists" as status;'
END;
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = CASE
    WHEN @effectiveness_score_prompts_exists = 0 THEN 'ALTER TABLE prompts ADD COLUMN effectiveness_score DECIMAL(3,2) DEFAULT 0.00 AFTER usage_count;'
    ELSE 'SELECT "effectiveness_score column already exists" as status;'
END;
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = CASE
    WHEN @examples_exists = 0 THEN 'ALTER TABLE prompts ADD COLUMN examples TEXT AFTER effectiveness_score;'
    ELSE 'SELECT "examples column already exists" as status;'
END;
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = CASE
    WHEN @author_exists = 0 THEN 'ALTER TABLE prompts ADD COLUMN author VARCHAR(50) AFTER examples;'
    ELSE 'SELECT "author column already exists" as status;'
END;
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = CASE
    WHEN @is_public_prompts_exists = 0 THEN 'ALTER TABLE prompts ADD COLUMN is_public BOOLEAN DEFAULT TRUE AFTER author;'
    ELSE 'SELECT "is_public column already exists" as status;'
END;
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 插入初始提示词数据
INSERT INTO prompts (name, content, category, style, description, examples, author, is_public) VALUES
('角色性格塑造', '你是一位专业的小说角色设计师。请根据以下信息设计一个立体、有深度的角色：\n\n角色基本信息：{基本信息}\n\n请提供：\n1. 性格特点（至少5个核心特质）\n2. 背景故事（成长经历、重要事件）\n3. 内在冲突（内心矛盾、挣扎）\n4. 外在目标（短期和长期目标）\n5. 说话方式（语言习惯、口头禅）\n6. 行为模式（习惯动作、小细节）', 'CHARACTER', '专业', '用于设计小说角色的专业提示词', '角色基本信息：一个年轻的魔法师学徒，出身贫寒但天赋异禀', 'AI系统', true),
('情节冲突设计', '你是一位专业的情节冲突设计师。请根据以下信息设计一个引人入胜的情节冲突：\n\n故事背景：{背景信息}\n\n请提供：\n1. 核心冲突（主要矛盾）\n2. 冲突升级（如何逐步激化）\n3. 转折点（意外事件、关键选择）\n4. 解决方式（冲突如何化解）\n5. 情感冲击（对角色和读者的影响）', 'PLOT', '专业', '用于设计情节冲突的专业提示词', '故事背景：一个被诅咒的王国，只有找到失落的圣物才能解除诅咒', 'AI系统', true),
('世界观构建', '你是一位专业的世界观构建师。请根据以下信息设计一个完整、合理的世界观：\n\n世界类型：{世界类型}\n\n请提供：\n1. 地理环境（地形、气候、资源）\n2. 社会结构（政治制度、阶级关系）\n3. 文化传统（信仰、习俗、艺术）\n4. 科技水平（技术发展、魔法体系）\n5. 历史背景（重要事件、传说）\n6. 种族设定（不同族群、关系）', 'WORLD_BUILDING', '专业', '用于构建世界观的专业提示词', '世界类型：一个融合魔法与科技的蒸汽朋克世界', 'AI系统', true),
('情感描写技巧', '你是一位专业的情感描写专家。请根据以下情境创作一段细腻的情感描写：\n\n情感类型：{情感类型}\n情境描述：{情境描述}\n\n要求：\n1. 避免直接说出情感名称\n2. 通过细节和动作表现情感\n3. 营造强烈的代入感\n4. 字数控制在200字以内', 'EMOTION', '文学', '用于情感描写的专业提示词', '情感类型：失去亲人的悲痛\n情境描述：在医院的走廊里等待手术结果', 'AI系统', true),
('动作场景描写', '你是一位专业的动作场景描写专家。请根据以下信息创作一段紧张刺激的动作场景：\n\n场景类型：{场景类型}\n参与角色：{参与角色}\n\n要求：\n1. 节奏紧凑，紧张刺激\n2. 动作描写具体生动\n3. 注意细节和节奏控制\n4. 字数控制在300字以内', 'ACTION', '紧张', '用于动作场景描写的专业提示词', '场景类型：剑术对决\n参与角色：两位剑术大师在古堡中决斗', 'AI系统', true)
ON DUPLICATE KEY UPDATE
    content = VALUES(content),
    category = VALUES(category),
    style = VALUES(style),
    description = VALUES(description),
    examples = VALUES(examples),
    author = VALUES(author),
    is_public = VALUES(is_public);

-- 创建索引优化查询性能（安全创建，避免重复）
-- 先删除存储过程（如果存在）
DROP PROCEDURE IF EXISTS CreateIndexIfNotExists;

-- 创建安全索引的存储过程
DELIMITER //
CREATE PROCEDURE CreateIndexIfNotExists(
    IN index_name VARCHAR(64),
    IN table_name_param VARCHAR(64),
    IN index_definition TEXT
)
BEGIN
    DECLARE index_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO index_exists
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
    AND table_name = table_name_param
    AND index_name = index_name;

    IF index_exists = 0 THEN
        SET @sql = CONCAT('CREATE INDEX ', index_name, ' ON ', table_name_param, ' ', index_definition);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- 使用存储过程创建索引
CALL CreateIndexIfNotExists('idx_novels_status_created_at', 'novels', '(status, created_at)');
CALL CreateIndexIfNotExists('idx_chapters_novel_order', 'chapters', '(novel_id, order_num)');
CALL CreateIndexIfNotExists('idx_characters_novel_name', 'characters', '(novel_id, name)');
CALL CreateIndexIfNotExists('idx_prompts_category_style', 'prompts', '(category, style)');
CALL CreateIndexIfNotExists('idx_ai_tasks_type_status', 'ai_tasks', '(type, status)');
CALL CreateIndexIfNotExists('idx_foreshadowing_novel_status', 'foreshadowing', '(novel_id, status)');
CALL CreateIndexIfNotExists('idx_plot_points_novel_type', 'plot_points', '(novel_id, type)');
CALL CreateIndexIfNotExists('idx_writing_techniques_category_difficulty', 'writing_techniques', '(category, difficulty_level)');

-- 创建视图简化常用查询
CREATE OR REPLACE VIEW novel_summary AS
SELECT
    n.id,
    n.title,
    n.genre,
    n.status,
    n.word_count,
    n.chapter_count,
    u.username as author,
    n.created_at,
    p.completion_rate,
    p.current_stage
FROM novels n
LEFT JOIN users u ON n.created_by = u.id
LEFT JOIN progress p ON n.id = p.novel_id;

CREATE OR REPLACE VIEW chapter_progress AS
SELECT
    n.id as novel_id,
    n.title as novel_title,
    COUNT(c.id) as total_chapters,
    SUM(CASE WHEN c.status = 'COMPLETED' THEN 1 ELSE 0 END) as completed_chapters,
    SUM(c.word_count) as total_words,
    AVG(c.word_count) as avg_chapter_length
FROM novels n
LEFT JOIN chapters c ON n.id = c.novel_id
GROUP BY n.id, n.title;

-- 创建存储过程
-- 先删除存储过程（如果存在）
DROP PROCEDURE IF EXISTS UpdateNovelProgress;
DROP PROCEDURE IF EXISTS GetNovelStatistics;

DELIMITER //

CREATE PROCEDURE UpdateNovelProgress(IN novel_id_param BIGINT)
BEGIN
    DECLARE total_chapters_var INT;
    DECLARE total_words_var INT;
    DECLARE novel_exists INT DEFAULT 0;

    -- 检查小说是否存在，避免在初始化期间的问题
    SELECT COUNT(*) INTO novel_exists
    FROM novels
    WHERE id = novel_id_param;

    IF novel_exists > 0 THEN
        -- 获取章节总数和总字数
        SELECT COUNT(*), COALESCE(SUM(word_count), 0)
        INTO total_chapters_var, total_words_var
        FROM chapters
        WHERE novel_id = novel_id_param;

        -- 更新小说统计信息（使用 IGNORE 避免锁定问题）
        UPDATE IGNORE novels
        SET chapter_count = total_chapters_var,
            word_count = total_words_var,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = novel_id_param;

        -- 更新进度信息
        UPDATE IGNORE progress
        SET total_chapters = total_chapters_var,
            word_count = total_words_var,
            completion_rate = CASE
                WHEN total_chapters_var > 0 THEN (current_chapter / total_chapters_var) * 100
                ELSE 0
            END,
            updated_at = CURRENT_TIMESTAMP
        WHERE novel_id = novel_id_param;
    END IF;
END //

CREATE PROCEDURE GetNovelStatistics(IN novel_id_param BIGINT)
BEGIN
    SELECT
        n.title,
        n.genre,
        n.status,
        n.word_count,
        n.chapter_count,
        p.completion_rate,
        p.current_stage,
        p.writing_streak,
        p.average_words_per_hour,
        COUNT(f.id) as foreshadowing_count,
        COUNT(pp.id) as plot_points_count,
        COUNT(c.id) as character_count
    FROM novels n
    LEFT JOIN progress p ON n.id = p.novel_id
    LEFT JOIN foreshadowing f ON n.id = f.novel_id
    LEFT JOIN plot_points pp ON n.id = pp.novel_id
    LEFT JOIN characters c ON n.id = c.novel_id
    WHERE n.id = novel_id_param
    GROUP BY n.id, n.title, n.genre, n.status, n.word_count, n.chapter_count,
             p.completion_rate, p.current_stage, p.writing_streak, p.average_words_per_hour;
END //

DELIMITER ;

-- ===========================================
-- 数据插入部分
-- ===========================================
-- 注意：触发器将在所有数据插入完成后创建，避免循环引用问题

-- 插入示例小说数据（使用子查询获取正确的用户ID）
INSERT INTO novels (title, description, genre, created_by)
SELECT '魔法学院的秘密', '一个关于魔法、友情和成长的故事', '奇幻', u.id
FROM users u WHERE u.username = 'author1'
UNION ALL
SELECT '都市异能传说', '现代都市中的超能力者故事', '都市', u.id
FROM users u WHERE u.username = 'author1'
ON DUPLICATE KEY UPDATE
    description = VALUES(description),
    genre = VALUES(genre);

-- 插入示例章节数据（使用子查询获取正确的小说ID）
-- 注意：由于触发器尚未创建，这里不会有循环引用问题
INSERT INTO chapters (title, content, order_num, novel_id, word_count)
SELECT '第一章 入学通知', '这是一个风和日丽的早晨...', 1, n.id, 100
FROM novels n WHERE n.title = '魔法学院的秘密'
UNION ALL
SELECT '第二章 魔法测试', '新生们聚集在广场上...', 2, n.id, 100
FROM novels n WHERE n.title = '魔法学院的秘密'
UNION ALL
SELECT '第一章 觉醒之夜', '深夜的街道上...', 1, n.id, 100
FROM novels n WHERE n.title = '都市异能传说'
ON DUPLICATE KEY UPDATE
    content = VALUES(content),
    word_count = VALUES(word_count);

-- 插入示例角色数据（使用子查询获取正确的小说ID）
INSERT INTO characters (name, description, personality, role, novel_id)
SELECT '艾莉娅', '魔法学院的新生，拥有罕见的元素亲和力', '勇敢、善良、好奇心强', '主角', n.id
FROM novels n WHERE n.title = '魔法学院的秘密'
UNION ALL
SELECT '雷恩', '艾莉娅的室友，擅长火系魔法', '热情、冲动、讲义气', '重要配角', n.id
FROM novels n WHERE n.title = '魔法学院的秘密'
UNION ALL
SELECT '林风', '都市异能者，能够操控风元素', '冷静、理性、正义感强', '主角', n.id
FROM novels n WHERE n.title = '都市异能传说'
ON DUPLICATE KEY UPDATE
    description = VALUES(description),
    personality = VALUES(personality),
    role = VALUES(role);

-- 插入示例世界观数据（使用子查询获取正确的小说ID）
INSERT INTO world_views (name, background, novel_id)
SELECT '魔法世界', '一个魔法与科技并存的世界，魔法学院培养着未来的魔法师', n.id
FROM novels n WHERE n.title = '魔法学院的秘密'
UNION ALL
SELECT '现代都市', '表面平静的现代都市，暗藏着超能力者的秘密组织', n.id
FROM novels n WHERE n.title = '都市异能传说'
ON DUPLICATE KEY UPDATE
    background = VALUES(background);

-- 插入示例进度数据（使用子查询获取正确的小说ID）
INSERT INTO progress (novel_id, total_chapters, word_count, target_word_count)
SELECT n.id, 2, 200, 50000
FROM novels n WHERE n.title = '魔法学院的秘密'
UNION ALL
SELECT n.id, 1, 100, 80000
FROM novels n WHERE n.title = '都市异能传说'
ON DUPLICATE KEY UPDATE
    total_chapters = VALUES(total_chapters),
    word_count = VALUES(word_count),
    target_word_count = VALUES(target_word_count);

-- 插入示例伏笔数据（使用子查询获取正确的ID）
INSERT INTO foreshadowing (content, type, planned_chapter, description, importance_level, novel_id, created_by)
SELECT '艾莉娅的项链在月光下发出微弱的光芒', 'MYSTERY', 5, '关于艾莉娅身世的伏笔', 4, n.id, u.id
FROM novels n, users u WHERE n.title = '魔法学院的秘密' AND u.username = 'author1'
UNION ALL
SELECT '雷恩对火系魔法的异常敏感', 'CHARACTER_DEVELOPMENT', 8, '雷恩隐藏能力的伏笔', 3, n.id, u.id
FROM novels n, users u WHERE n.title = '魔法学院的秘密' AND u.username = 'author1'
ON DUPLICATE KEY UPDATE
    description = VALUES(description),
    importance_level = VALUES(importance_level);

-- 插入示例剧情节点数据（使用子查询获取正确的ID）
INSERT INTO plot_points (title, type, planned_chapter, importance, description, novel_id, created_by)
SELECT '魔法测试的意外', 'INCITING_INCIDENT', 2, 'HIGH', '艾莉娅在魔法测试中展现出惊人的天赋', n.id, u.id
FROM novels n, users u WHERE n.title = '魔法学院的秘密' AND u.username = 'author1'
UNION ALL
SELECT '第一次实战', 'FIRST_PLOT_POINT', 5, 'CRITICAL', '艾莉娅第一次面对真正的危险', n.id, u.id
FROM novels n, users u WHERE n.title = '魔法学院的秘密' AND u.username = 'author1'
ON DUPLICATE KEY UPDATE
    description = VALUES(description),
    importance = VALUES(importance);

-- 提交事务
COMMIT;

-- ===========================================
-- 多AI协作系统相关表
-- ===========================================

-- AI代理表
CREATE TABLE IF NOT EXISTS ai_agents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    role ENUM('progress_analyst', 'foreshadowing_expert', 'plot_planner', 'stage_evaluator', 'style_advisor', 'character_expert') NOT NULL,
    description TEXT,
    capabilities JSON,
    status ENUM('active', 'busy', 'offline') NOT NULL DEFAULT 'active',
    expertise INT NOT NULL DEFAULT 5 COMMENT '专业度评分1-10',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_role (role),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI代理表';

-- AI协作任务表
CREATE TABLE IF NOT EXISTS ai_collaboration_tasks (
    id VARCHAR(50) PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    type ENUM('analysis', 'review', 'planning', 'optimization') NOT NULL,
    status ENUM('pending', 'in_progress', 'completed', 'failed') NOT NULL DEFAULT 'pending',
    assigned_agents JSON COMMENT '分配的AI代理ID列表',
    input_data JSON COMMENT '输入数据',
    output_data JSON COMMENT '输出数据',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    novel_id BIGINT,
    chapter_id BIGINT,
    INDEX idx_type (type),
    INDEX idx_status (status),
    INDEX idx_novel_id (novel_id),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI协作任务表';

-- AI审核会话表
CREATE TABLE IF NOT EXISTS ai_review_sessions (
    id VARCHAR(50) PRIMARY KEY,
    chapter_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewers JSON COMMENT 'AI审核者结果列表',
    overall_score DECIMAL(3,1) DEFAULT 0.0,
    consensus_recommendations JSON COMMENT '一致性建议',
    conflicting_opinions JSON COMMENT '冲突意见',
    status ENUM('pending', 'reviewing', 'completed') NOT NULL DEFAULT 'pending',
    FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE CASCADE,
    INDEX idx_chapter_id (chapter_id),
    INDEX idx_timestamp (timestamp),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI审核会话表';

-- ===========================================
-- 知识库系统相关表
-- ===========================================

-- 知识库内容表
CREATE TABLE IF NOT EXISTS knowledge_base (
    id VARCHAR(50) PRIMARY KEY,
    category ENUM('technique', 'style', 'plot_pattern', 'character_archetype', 'world_building') NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    examples JSON COMMENT '示例列表',
    tags JSON COMMENT '标签列表',
    effectiveness DECIMAL(3,1) DEFAULT 0.0,
    usage_count INT DEFAULT 0,
    source ENUM('ai_generated', 'user_input', 'reference_extracted') NOT NULL DEFAULT 'ai_generated',
    applicable_genres JSON COMMENT '适用体裁列表',
    difficulty INT DEFAULT 1 COMMENT '难度等级1-5',
    related_knowledge JSON COMMENT '相关知识ID列表',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_category (category),
    INDEX idx_title (title),
    INDEX idx_effectiveness (effectiveness),
    INDEX idx_source (source),
    INDEX idx_created_by (created_by),
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库内容表';

-- 小说片段表
CREATE TABLE IF NOT EXISTS novel_segments (
    id VARCHAR(50) PRIMARY KEY,
    novel_id VARCHAR(50) NOT NULL,
    chapter_number INT NOT NULL,
    segment_order INT NOT NULL,
    content TEXT NOT NULL,
    word_count INT DEFAULT 0,
    quality_score DECIMAL(3,1) DEFAULT 0.0,
    techniques JSON COMMENT '使用的写作技巧',
    tags JSON COMMENT '片段标签',
    emotional_tone VARCHAR(50),
    pace_level INT DEFAULT 5,
    preceding_context TEXT,
    following_context TEXT,
    chapter_context TEXT,
    reference_novel_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_novel_id (novel_id),
    INDEX idx_chapter_number (chapter_number),
    INDEX idx_quality_score (quality_score),
    INDEX idx_reference_novel_id (reference_novel_id),
    FOREIGN KEY (reference_novel_id) REFERENCES reference_novels(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小说片段分析表';

-- ===========================================
-- 项目管理和进度跟踪相关表
-- ===========================================

-- 项目里程碑表
CREATE TABLE IF NOT EXISTS project_milestones (
    id VARCHAR(50) PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    target_date DATE NOT NULL,
    status ENUM('pending', 'in_progress', 'completed', 'delayed', 'cancelled') NOT NULL DEFAULT 'pending',
    type ENUM('chapter', 'phase', 'quality', 'deadline') NOT NULL,
    requirements JSON COMMENT '完成要求列表',
    completed_at TIMESTAMP NULL,
    rewards VARCHAR(500),
    novel_id BIGINT NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_novel_id (novel_id),
    INDEX idx_status (status),
    INDEX idx_type (type),
    INDEX idx_target_date (target_date),
    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目里程碑表';

-- 项目截止日期表
CREATE TABLE IF NOT EXISTS project_deadlines (
    id VARCHAR(50) PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    date DATE NOT NULL,
    type ENUM('soft', 'hard') NOT NULL DEFAULT 'soft',
    priority ENUM('low', 'medium', 'high', 'critical') NOT NULL DEFAULT 'medium',
    description TEXT,
    status ENUM('upcoming', 'due_soon', 'overdue', 'completed') NOT NULL DEFAULT 'upcoming',
    related_milestones JSON COMMENT '相关里程碑ID列表',
    novel_id BIGINT NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_novel_id (novel_id),
    INDEX idx_date (date),
    INDEX idx_status (status),
    INDEX idx_priority (priority),
    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目截止日期表';
-- 写作会话表
CREATE TABLE IF NOT EXISTS writing_sessions (
    id VARCHAR(50) PRIMARY KEY,
    novel_id BIGINT NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NULL,
    duration INT DEFAULT 0 COMMENT '持续时间(分钟)',
    words_written INT DEFAULT 0,
    chapters_worked JSON COMMENT '工作的章节ID列表',
    quality DECIMAL(3,1) DEFAULT 0.0,
    ai_usage DECIMAL(3,1) DEFAULT 0.0 COMMENT 'AI辅助程度0-100',
    mood ENUM('excellent', 'good', 'average', 'poor', 'terrible') DEFAULT 'average',
    notes TEXT,
    achievements JSON COMMENT '成就列表',
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_novel_id (novel_id),
    INDEX idx_user_id (user_id),
    INDEX idx_start_time (start_time),
    INDEX idx_duration (duration),
    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='写作会话记录表';

-- 写作连击记录表
CREATE TABLE IF NOT EXISTS writing_streaks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    novel_id BIGINT NOT NULL UNIQUE,
    current_streak INT DEFAULT 0,
    longest_streak INT DEFAULT 0,
    streak_start_date DATE NULL,
    last_writing_date DATE NULL,
    streak_type ENUM('daily', 'word_count', 'quality') NOT NULL DEFAULT 'daily',
    target INT NOT NULL DEFAULT 1000,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_novel_id (novel_id),
    INDEX idx_user_id (user_id),
    INDEX idx_current_streak (current_streak)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='写作连击记录表';

-- 项目分析数据表
CREATE TABLE IF NOT EXISTS project_analytics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    novel_id BIGINT NOT NULL,
    period ENUM('week', 'month', 'quarter', 'year', 'all') NOT NULL DEFAULT 'month',

    -- 写作量分析
    total_words_written INT DEFAULT 0,
    average_words_per_day DECIMAL(8,2) DEFAULT 0.00,
    most_productive_day DATE NULL,
    word_count_trend JSON COMMENT '词数趋势数据',

    -- 时间分析
    total_time_spent BIGINT DEFAULT 0 COMMENT '总时间(分钟)',
    average_session_length DECIMAL(8,2) DEFAULT 0.00,
    most_productive_time_of_day TIME NULL,
    time_distribution JSON COMMENT '时间分布数据',

    -- 质量分析
    average_quality DECIMAL(3,1) DEFAULT 0.0,
    quality_trend JSON COMMENT '质量趋势数据',
    ai_assistance_usage DECIMAL(3,1) DEFAULT 0.0,
    revision_frequency DECIMAL(3,1) DEFAULT 0.0,

    -- 进度分析
    progress_velocity DECIMAL(5,2) DEFAULT 0.00 COMMENT '章节/天',
    predicted_completion DATE NULL,
    bottlenecks JSON COMMENT '瓶颈列表',
    recommendations JSON COMMENT '建议列表',

    analysis_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE,
    INDEX idx_novel_id (novel_id),
    INDEX idx_period (period),
    INDEX idx_analysis_date (analysis_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目分析数据表';

-- ===========================================
-- 章节规划相关表
-- ===========================================

-- 章节规划表
CREATE TABLE IF NOT EXISTS chapter_plans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chapter_number INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    phase ENUM('opening', 'development', 'climax', 'resolution') NOT NULL,
    main_goal TEXT,
    key_events JSON COMMENT '关键事件列表',
    character_focus JSON COMMENT '重点角色列表',
    plot_threads JSON COMMENT '情节线列表',
    estimated_word_count INT DEFAULT 2000,
    priority ENUM('high', 'medium', 'low') NOT NULL DEFAULT 'medium',
    status ENUM('planned', 'in_progress', 'completed') NOT NULL DEFAULT 'planned',
    novel_id BIGINT NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_novel_id (novel_id),
    INDEX idx_chapter_number (chapter_number),
    INDEX idx_phase (phase),
    INDEX idx_status (status),
    INDEX idx_priority (priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='章节规划表';

-- 伏笔计划表
CREATE TABLE IF NOT EXISTS foreshadowing_plans (
    id VARCHAR(50) PRIMARY KEY,
    content TEXT NOT NULL,
    plant_chapter INT NOT NULL,
    reveal_chapter INT NOT NULL,
    importance ENUM('high', 'medium', 'low') NOT NULL DEFAULT 'medium',
    status ENUM('planned', 'planted', 'revealed') NOT NULL DEFAULT 'planned',
    novel_id BIGINT NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_novel_id (novel_id),
    INDEX idx_plant_chapter (plant_chapter),
    INDEX idx_reveal_chapter (reveal_chapter),
    INDEX idx_importance (importance),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='伏笔计划表';

-- 场景规划表
CREATE TABLE IF NOT EXISTS scene_plans (
    id VARCHAR(50) PRIMARY KEY,
    location VARCHAR(200) NOT NULL,
    purpose VARCHAR(200) NOT NULL,
    characters JSON COMMENT '参与角色列表',
    key_actions JSON COMMENT '关键动作列表',
    mood VARCHAR(50),
    estimated_duration INT DEFAULT 30 COMMENT '预计时长(分钟)',
    chapter_plan_id BIGINT,
    order_in_chapter INT DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (chapter_plan_id) REFERENCES chapter_plans(id) ON DELETE CASCADE,
    INDEX idx_chapter_plan_id (chapter_plan_id),
    INDEX idx_order_in_chapter (order_in_chapter),
    INDEX idx_location (location)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='场景规划表';
-- ===========================================
-- 大纲管理相关表
-- ===========================================

-- 小说大纲主表
CREATE TABLE IF NOT EXISTS novel_outlines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    novel_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    genre VARCHAR(100),
    basic_idea TEXT,
    core_theme TEXT,
    main_characters TEXT,
    plot_structure TEXT,
    world_setting TEXT,
    key_elements JSON COMMENT '关键元素列表',
    conflict_types JSON COMMENT '冲突类型列表',
    target_word_count INT DEFAULT 50000,
    target_chapter_count INT DEFAULT 20,
    status ENUM('DRAFT', 'CONFIRMED', 'REVISED') NOT NULL DEFAULT 'DRAFT',
    is_ai_generated BOOLEAN DEFAULT FALSE,
    last_modified_by_ai VARCHAR(100),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_novel_id (novel_id),
    INDEX idx_status (status),
    INDEX idx_created_by (created_by),
    INDEX idx_genre (genre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小说大纲主表';

-- 小说大纲详细表 (扩展现有的novel_outlines)
CREATE TABLE IF NOT EXISTS outline_details (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    outline_id BIGINT NOT NULL,
    section_type ENUM('theme', 'characters', 'plot_structure', 'world_setting', 'key_elements', 'conflicts') NOT NULL,
    section_data JSON NOT NULL COMMENT '章节详细数据',
    order_num INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (outline_id) REFERENCES novel_outlines(id) ON DELETE CASCADE,
    INDEX idx_outline_id (outline_id),
    INDEX idx_section_type (section_type),
    INDEX idx_order_num (order_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='大纲详细内容表';

CREATE TABLE IF NOT EXISTS novel_volumes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    novel_id BIGINT NOT NULL,
    outline_id BIGINT,
    title VARCHAR(200) NOT NULL,
    theme VARCHAR(200),
    description TEXT,
    content_outline TEXT,
    volume_number INT NOT NULL,
    chapter_start INT NOT NULL,
    chapter_end INT NOT NULL,
    estimated_word_count INT DEFAULT 0,
    actual_word_count INT DEFAULT 0,
    key_events TEXT,
    character_development TEXT,
    plot_threads TEXT,
    status ENUM('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'REVISED') NOT NULL DEFAULT 'PLANNED',
    is_ai_generated BOOLEAN DEFAULT FALSE,
    last_modified_by_ai VARCHAR(100),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE,
    FOREIGN KEY (outline_id) REFERENCES novel_outlines(id) ON DELETE SET NULL,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_novel_id (novel_id),
    INDEX idx_outline_id (outline_id),
    INDEX idx_volume_number (volume_number),
    INDEX idx_status (status),
    INDEX idx_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小说卷表';

-- 若历史库中 novel_volumes.id 为 VARCHAR，安全迁移为 BIGINT AUTO_INCREMENT
SET @nv_id_type = (
    SELECT DATA_TYPE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'novel_volumes' AND COLUMN_NAME = 'id'
);

SET @alter_sql = CASE
    WHEN @nv_id_type = 'varchar' THEN 'ALTER TABLE novel_volumes MODIFY COLUMN id BIGINT NOT NULL'
    ELSE 'SELECT "novel_volumes.id already BIGINT" as status'
END;

PREPARE stmt FROM @alter_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 再次设置为 AUTO_INCREMENT（不重复声明 PRIMARY KEY，避免1068错误）
SET @auto_inc_sql = CASE
    WHEN @nv_id_type = 'varchar' THEN 'ALTER TABLE novel_volumes MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT'
    ELSE 'SELECT "novel_volumes.id already AUTO_INCREMENT or BIGINT" as status'
END;
PREPARE stmt2 FROM @auto_inc_sql; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

-- 作品定位表
CREATE TABLE IF NOT EXISTS work_positions (
    id VARCHAR(50) PRIMARY KEY,
    novel_id BIGINT NOT NULL,
    target_audience VARCHAR(100),
    writing_style VARCHAR(100),
    narrative_perspective VARCHAR(50),
    tone VARCHAR(100),
    themes JSON COMMENT '主题列表',
    unique_selling_points JSON COMMENT '独特卖点列表',
    comparable_works JSON COMMENT '可比作品列表',
    market_positioning TEXT,
    status ENUM('DRAFT', 'ACTIVE', 'ARCHIVED') NOT NULL DEFAULT 'DRAFT',
    is_ai_generated BOOLEAN DEFAULT FALSE,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_novel_id (novel_id),
    INDEX idx_status (status),
    INDEX idx_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作品定位表';

-- ===========================================
-- 用户会话和认证相关表
-- ===========================================

-- 用户会话表
CREATE TABLE IF NOT EXISTS user_sessions (
    id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    ip_address VARCHAR(50),
    user_agent TEXT,
    login_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_activity TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    device_info JSON,
    location_info JSON,

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_expires_at (expires_at),
    INDEX idx_is_active (is_active),
    INDEX idx_last_activity (last_activity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户会话管理表';

-- 登录历史表
CREATE TABLE IF NOT EXISTS login_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    login_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(50),
    user_agent TEXT,
    login_result ENUM('success', 'failed', 'blocked') NOT NULL,
    failure_reason VARCHAR(200),
    device_info JSON,
    location_info JSON,

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_login_time (login_time),
    INDEX idx_login_result (login_result),
    INDEX idx_ip_address (ip_address)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录历史记录表';
-- ===========================================
-- 写作建议和推荐相关表
-- ===========================================

-- 写作建议表
CREATE TABLE IF NOT EXISTS writing_suggestions (
    id VARCHAR(50) PRIMARY KEY,
    novel_id BIGINT NOT NULL,
    chapter_id BIGINT,
    suggestions JSON NOT NULL COMMENT '建议列表',
    priority ENUM('low', 'medium', 'high') NOT NULL DEFAULT 'medium',
    category ENUM('plot', 'character', 'style', 'pacing') NOT NULL,
    reasoning TEXT,
    applied BOOLEAN DEFAULT FALSE,
    applied_at TIMESTAMP NULL,
    effectiveness_rating INT DEFAULT NULL COMMENT '用户评分1-5',
    created_by_agent VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE,
    FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE CASCADE,
    INDEX idx_novel_id (novel_id),
    INDEX idx_chapter_id (chapter_id),
    INDEX idx_priority (priority),
    INDEX idx_category (category),
    INDEX idx_applied (applied),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='写作建议表';

-- ===========================================
-- 写作模板相关表
-- ===========================================

-- 写作模板表
CREATE TABLE IF NOT EXISTS writing_templates (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    category ENUM('character', 'scene', 'dialogue', 'plot', 'transition') NOT NULL,
    template TEXT NOT NULL,
    variables JSON COMMENT '模板变量定义',
    usage_count INT DEFAULT 0,
    rating DECIMAL(3,1) DEFAULT 0.0,
    tags JSON COMMENT '标签列表',
    is_public BOOLEAN DEFAULT TRUE,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_name (name),
    INDEX idx_category (category),
    INDEX idx_rating (rating),
    INDEX idx_is_public (is_public),
    INDEX idx_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='写作模板表';

-- ===========================================
-- 系统设置和配置表
-- ===========================================

-- 用户写作设置表
CREATE TABLE IF NOT EXISTS user_writing_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    daily_word_goal INT DEFAULT 1000,
    preferred_writing_time TIME DEFAULT '09:00:00',
    default_genre VARCHAR(50),
    ai_assistance_level ENUM('none', 'low', 'medium', 'high') DEFAULT 'medium',
    auto_save_interval INT DEFAULT 300 COMMENT '自动保存间隔(秒)',
    theme_preference ENUM('light', 'dark', 'auto') DEFAULT 'light',
    notification_settings JSON COMMENT '通知设置',
    writing_reminders JSON COMMENT '写作提醒设置',
    quality_threshold DECIMAL(3,1) DEFAULT 7.0,
    backup_settings JSON COMMENT '备份设置',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户写作设置表';

-- ===========================================
-- 索引优化
-- ===========================================

-- 为新表创建复合索引提升查询性能（使用存储过程安全创建）
CALL CreateIndexIfNotExists('idx_ai_collaboration_novel_status', 'ai_collaboration_tasks', '(novel_id, status)');
CALL CreateIndexIfNotExists('idx_writing_sessions_user_novel', 'writing_sessions', '(user_id, novel_id)');
CALL CreateIndexIfNotExists('idx_chapter_plans_novel_phase', 'chapter_plans', '(novel_id, phase)');
CALL CreateIndexIfNotExists('idx_foreshadowing_plans_novel_plant', 'foreshadowing_plans', '(novel_id, plant_chapter)');
CALL CreateIndexIfNotExists('idx_writing_suggestions_novel_category', 'writing_suggestions', '(novel_id, category)');
CALL CreateIndexIfNotExists('idx_project_analytics_novel_period', 'project_analytics', '(novel_id, period)');

-- ===========================================
-- 初始数据插入
-- ===========================================

-- 插入默认AI代理
INSERT INTO ai_agents (name, role, description, capabilities, expertise) VALUES
('进度分析师', 'progress_analyst', '专门分析小说创作进度，评估当前阶段，提供下一步建议', '["进度跟踪", "阶段评估", "时间规划", "目标设定"]', 9),
('伏笔管理专家', 'foreshadowing_expert', '管理小说中的伏笔系统，确保前后呼应', '["伏笔识别", "时机提醒", "关联分析", "完成检查"]', 8),
('剧情规划师', 'plot_planner', '规划高能剧情和转折点，平衡故事节奏', '["情节设计", "节奏控制", "冲突升级", "高潮规划"]', 9),
('阶段评估专家', 'stage_evaluator', '评估当前创作阶段，提供阶段转换建议', '["阶段判断", "目标完成度", "转换建议", "连贯性检查"]', 8),
('文风顾问', 'style_advisor', '分析和优化写作风格，提升文笔质量', '["风格分析", "语言优化", "表达改进", "读者体验"]', 9),
('角色专家', 'character_expert', '深度分析角色发展，确保人物一致性', '["角色分析", "性格一致性", "关系管理", "成长轨迹"]', 8)
ON DUPLICATE KEY UPDATE
    description = VALUES(description),
    capabilities = VALUES(capabilities),
    expertise = VALUES(expertise);

INSERT INTO knowledge_base (
    id,
    category,
    title,
    content,
    examples,
    effectiveness,
    applicable_genres,
    difficulty
) VALUES
('technique_001', 'technique', '对话推进情节', '通过角色对话来推进故事情节，而不是纯粹的叙述描写。好的对话应该既展现角色性格，又推进故事发展。',
'["\\"我不会让你一个人去的,\\"她坚定地说,\\"不管发生什么，我们一起面对。\\"", "\\"真相比你想象的更复杂,\\"老人叹了口气,\\"有些秘密，还不是时候告诉你。\\""]',
8.5,
'["奇幻", "都市", "悬疑", "科幻"]',
3),

('style_001', 'style', '感官描写技巧', '运用五感描写来增强读者的沉浸体验。不要只依赖视觉描写，听觉、触觉、味觉、嗅觉同样重要。',
'["空气中弥漫着淡淡的桂花香，混合着即将到来的雨水气息。", "他的手心冒着冷汗，紧握的拳头传来指甲刺入掌心的刺痛。"]',
9.2,
'["奇幻", "都市", "历史", "悬疑"]',
2),

('plot_001', 'plot_pattern', '英雄之旅模式', '经典的故事结构模式：普通世界-冒险召唤-拒绝召唤-遇见导师-越过第一道门槛-盟友敌人试炼-进入洞穴-磨难考验-获得宝剑-归途-复活-带着仙药回归。',
'["哈利波特系列：从普通男孩到魔法师的成长之路", "指环王：弗罗多的冒险旅程"]',
9.8,
'["奇幻", "冒险", "科幻", "神话"]',
4)
ON DUPLICATE KEY UPDATE
    content = VALUES(content),
    examples = VALUES(examples),
    effectiveness = VALUES(effectiveness),
    applicable_genres = VALUES(applicable_genres),
    difficulty = VALUES(difficulty);

-- 插入默认写作模板
INSERT INTO writing_templates (id, name, description, category, template, variables, tags) VALUES
('template_001', '角色初次登场模板', '用于描写重要角色初次登场的场景模板', 'character', '{character_name}站在{location}，{appearance_description}。{action_description}，{personality_hint}从{his_her}的{feature}中可见一斑。', '[{"name": "character_name", "description": "角色姓名", "type": "text", "required": true}, {"name": "location", "description": "登场地点", "type": "text", "required": true}, {"name": "appearance_description", "description": "外貌描写", "type": "text", "required": true}]', '["角色描写", "初次登场", "人物塑造"]'),
('template_002', '场景转换模板', '用于自然地进行场景转换的模板', 'transition', '{time_indicator}，{character_name}{transition_action}。{scene_description}在{his_her}眼前展开，{mood_description}。', '[{"name": "time_indicator", "description": "时间指示器", "type": "select", "options": ["片刻之后", "不久", "转眼间", "随着时间流逝"], "required": true}]', '["场景转换", "过渡", "叙述技巧"]')
ON DUPLICATE KEY UPDATE
    description = VALUES(description),
    category = VALUES(category),
    template = VALUES(template),
    variables = VALUES(variables),
    tags = VALUES(tags);

-- 为现有小说创建默认进度记录
INSERT INTO progress (novel_id, current_chapter, total_chapters, word_count, target_word_count, daily_word_goal)
SELECT id, 1, chapter_count, word_count, 50000, 2000
FROM novels
WHERE id NOT IN (SELECT novel_id FROM progress);

-- 为现有小说创建写作连击记录
INSERT INTO writing_streaks (novel_id, user_id, target)
SELECT n.id, n.created_by, 1000
FROM novels n
WHERE n.id NOT IN (SELECT COALESCE(novel_id, 0) FROM writing_streaks);

-- 为所有用户创建默认写作设置
INSERT INTO user_writing_settings (user_id, daily_word_goal, ai_assistance_level)
SELECT id, 1000, 'medium'
FROM users
WHERE id NOT IN (SELECT COALESCE(user_id, 0) FROM user_writing_settings);

-- 为现有小说创建示例大纲（使用子查询获取正确的ID）
INSERT INTO novel_outlines (novel_id, title, genre, basic_idea, core_theme, main_characters, plot_structure, world_setting, target_word_count, target_chapter_count, status, created_by)
SELECT n.id, '魔法学院的秘密', '奇幻', '一个普通少女进入魔法学院，发现自己拥有特殊能力的故事', '成长与友情的力量', '艾莉娅：主角，拥有罕见的元素亲和力\n雷恩：好友，火系魔法天才\n导师：神秘的魔法老师', '第一幕：入学与适应\n第二幕：发现能力与面临挑战\n第三幕：最终考验与成长', '魔法学院：一个培养魔法师的神秘学府\n魔法世界：魔法与科技并存的奇幻世界', 50000, 20, 'CONFIRMED', u.id
FROM novels n, users u WHERE n.title = '魔法学院的秘密' AND u.username = 'author1'
UNION ALL
SELECT n.id, '都市异能传说', '都市', '现代都市中隐藏着超能力者的秘密组织', '正义与力量的选择', '林风：主角，风系异能者\n组织成员：各种异能者\n反派：滥用异能的恶势力', '第一幕：觉醒异能\n第二幕：加入组织与训练\n第三幕：对抗邪恶势力', '现代都市：表面平静实则暗流涌动\n异能组织：维护平衡的秘密机构', 80000, 25, 'CONFIRMED', u.id
FROM novels n, users u WHERE n.title = '都市异能传说' AND u.username = 'author1'
ON DUPLICATE KEY UPDATE
    basic_idea = VALUES(basic_idea),
    core_theme = VALUES(core_theme),
    main_characters = VALUES(main_characters),
    plot_structure = VALUES(plot_structure),
    world_setting = VALUES(world_setting),
    target_word_count = VALUES(target_word_count),
    target_chapter_count = VALUES(target_chapter_count),
    status = VALUES(status);

-- 为示例大纲创建详细内容（使用子查询获取正确的大纲ID）
INSERT INTO outline_details (outline_id, section_type, section_data, order_num)
SELECT o.id, 'theme', '{"main_theme": "成长与友情", "sub_themes": ["勇气", "责任", "自我发现"], "message": "真正的力量来自内心的成长和朋友的支持"}', 1
FROM novel_outlines o WHERE o.title = '魔法学院的秘密'
UNION ALL
SELECT o.id, 'characters', '{"protagonist": {"name": "艾莉娅", "role": "主角", "arc": "从普通少女成长为强大魔法师"}, "supporting": [{"name": "雷恩", "role": "最佳朋友", "arc": "学会控制冲动，成为可靠伙伴"}]}', 2
FROM novel_outlines o WHERE o.title = '魔法学院的秘密'
UNION ALL
SELECT o.id, 'theme', '{"main_theme": "正义与选择", "sub_themes": ["责任", "牺牲", "团队合作"], "message": "力量越大，责任越大"}', 1
FROM novel_outlines o WHERE o.title = '都市异能传说'
UNION ALL
SELECT o.id, 'characters', '{"protagonist": {"name": "林风", "role": "主角", "arc": "从普通人觉醒异能，成为正义守护者"}, "supporting": [{"name": "组织导师", "role": "引路人", "arc": "传授经验与智慧"}]}', 2
FROM novel_outlines o WHERE o.title = '都市异能传说'
ON DUPLICATE KEY UPDATE
    section_data = VALUES(section_data),
    order_num = VALUES(order_num);

-- ===========================================
-- 清理和优化
-- ===========================================

-- 更新数据库统计信息（只分析存在的表）
SET @table_list = '';

-- 检查表是否存在并构建表列表
SELECT GROUP_CONCAT(table_name SEPARATOR ', ') INTO @table_list
FROM information_schema.tables
WHERE table_schema = 'ai_novel'
AND table_name IN ('ai_agents', 'ai_collaboration_tasks', 'knowledge_base', 'writing_sessions', 'chapter_plans', 'writing_suggestions');

-- 使用CASE语句替代IF语句来构建SQL
SET @sql = CASE
    WHEN @table_list IS NOT NULL AND @table_list != '' THEN CONCAT('ANALYZE TABLE ', @table_list)
    ELSE 'SELECT "No tables to analyze" as status'
END;

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 显示创建结果
SELECT 'Database initialization completed successfully!' as status;
SELECT COUNT(*) as total_tables FROM information_schema.tables WHERE table_schema = 'ai_novel';
SELECT COUNT(*) as total_users FROM users;
SELECT COUNT(*) as total_roles FROM roles;
SELECT COUNT(*) as total_novels FROM novels;
SELECT COUNT(*) as total_chapters FROM chapters;
SELECT COUNT(*) as total_characters FROM characters;
SELECT COUNT(*) as total_prompts FROM prompts;
SELECT COUNT(*) as total_techniques FROM writing_techniques;

-- 显示新增的表数量
SELECT COUNT(*) as new_tables_count
FROM information_schema.tables
WHERE table_schema = 'ai_novel'
AND table_name IN (
    'ai_agents', 'ai_collaboration_tasks', 'ai_review_sessions',
    'knowledge_base', 'novel_segments', 'project_milestones', 'project_deadlines',
    'writing_sessions', 'writing_streaks', 'project_analytics',
    'chapter_plans', 'foreshadowing_plans', 'scene_plans',
    'novel_outlines', 'outline_details', 'novel_volumes', 'work_positions',
    'user_sessions', 'login_history', 'writing_suggestions', 'writing_templates',
    'user_writing_settings'
);

-- ===========================================
-- 创建触发器（在所有数据插入完成后）
-- ===========================================

DELIMITER //

-- 安全删除已存在的触发器
DROP TRIGGER IF EXISTS after_chapter_update;
DROP TRIGGER IF EXISTS after_chapter_insert;
DROP TRIGGER IF EXISTS after_chapter_delete;

-- 章节字数变化时自动更新小说字数统计
CREATE TRIGGER after_chapter_update
AFTER UPDATE ON chapters
FOR EACH ROW
BEGIN
    IF OLD.word_count != NEW.word_count THEN
        CALL UpdateNovelProgress(NEW.novel_id);
    END IF;
END //

-- 新增章节时自动更新小说统计
CREATE TRIGGER after_chapter_insert
AFTER INSERT ON chapters
FOR EACH ROW
BEGIN
    CALL UpdateNovelProgress(NEW.novel_id);
END //

-- 删除章节时自动更新小说统计
CREATE TRIGGER after_chapter_delete
AFTER DELETE ON chapters
FOR EACH ROW
BEGIN
    CALL UpdateNovelProgress(OLD.novel_id);
END //

DELIMITER ;

-- 清理临时存储过程
DROP PROCEDURE IF EXISTS CreateIndexIfNotExists;

-- 重新启用外键检查
SET FOREIGN_KEY_CHECKS = 1;

-- 提交事务
COMMIT;

-- 显示完成信息
SELECT 'Database initialization completed successfully!' as message;
SELECT 'All tables, indexes, views, and initial data have been created.' as status;
SELECT 'Fixed all SQL syntax issues including index creation.' as fix_status;-- 确保 prompts 表存在 difficulty 字段（兼容老版本库）
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS add_prompts_difficulty()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'prompts' AND COLUMN_NAME = 'difficulty'
  ) THEN
    SET @ddl = 'ALTER TABLE prompts ADD COLUMN difficulty INT DEFAULT 0 AFTER description';
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END //
DELIMITER ;
CALL add_prompts_difficulty();
DROP PROCEDURE IF EXISTS add_prompts_difficulty;


SELECT 'Fixed all SQL syntax issues including index creation.' as fix_status;