-- =============================================
-- 写作系统数据库表设计
-- =============================================

-- 1. 文件夹表（支持用户自定义文件夹）
CREATE TABLE IF NOT EXISTS novel_folder (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL COMMENT '小说ID',
    folder_name VARCHAR(100) NOT NULL COMMENT '文件夹名称',
    folder_type VARCHAR(20) DEFAULT 'custom' COMMENT '类型: chapter-章节/custom-自定义',
    parent_id BIGINT DEFAULT NULL COMMENT '父文件夹ID（支持嵌套）',
    sort_order INT DEFAULT 0 COMMENT '排序顺序',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_novel_id (novel_id),
    INDEX idx_parent_id (parent_id),
    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE
) COMMENT='小说文件夹表';

-- 2. 文档表（统一管理章节和自定义文档）
CREATE TABLE IF NOT EXISTS novel_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL COMMENT '小说ID',
    folder_id BIGINT DEFAULT NULL COMMENT '所属文件夹ID',
    title VARCHAR(200) NOT NULL COMMENT '文档标题',
    content LONGTEXT COMMENT '文档内容',
    document_type VARCHAR(20) DEFAULT 'custom' COMMENT '类型: chapter-章节/custom-自定义',
    word_count INT DEFAULT 0 COMMENT '字数统计',
    sort_order INT DEFAULT 0 COMMENT '排序顺序',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_novel_id (novel_id),
    INDEX idx_folder_id (folder_id),
    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE,
    FOREIGN KEY (folder_id) REFERENCES novel_folder(id) ON DELETE CASCADE
) COMMENT='小说文档表';

-- 3. 参考文件表
CREATE TABLE IF NOT EXISTS reference_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL COMMENT '小说ID',
    file_name VARCHAR(255) NOT NULL COMMENT '文件名',
    file_type VARCHAR(20) NOT NULL COMMENT '文件类型: txt/docx',
    file_content LONGTEXT NOT NULL COMMENT '提取的文本内容',
    file_size BIGINT DEFAULT 0 COMMENT '文件大小（字节）',
    original_path VARCHAR(500) COMMENT '原文件存储路径',
    word_count INT DEFAULT 0 COMMENT '字数统计',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_novel_id (novel_id),
    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE
) COMMENT='参考文件表';

-- 4. AI对话历史表
CREATE TABLE IF NOT EXISTS ai_conversation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL COMMENT '小说ID',
    document_id BIGINT DEFAULT NULL COMMENT '关联的文档ID',
    generator_id BIGINT DEFAULT NULL COMMENT '使用的生成器ID',
    user_message TEXT COMMENT '用户输入消息',
    assistant_message LONGTEXT COMMENT 'AI回复内容',
    context_data JSON COMMENT '上下文数据（参考文件、关联文档等）',
    word_count INT DEFAULT 0 COMMENT '生成字数',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_novel_id (novel_id),
    INDEX idx_document_id (document_id),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (novel_id) REFERENCES novels(id) ON DELETE CASCADE,
    FOREIGN KEY (document_id) REFERENCES novel_document(id) ON DELETE SET NULL,
    FOREIGN KEY (generator_id) REFERENCES ai_generator(id) ON DELETE SET NULL
) COMMENT='AI对话历史表';

-- 插入默认章节文件夹（为每个小说创建）
-- 这个可以在创建小说时自动触发
-- INSERT INTO novel_folder (novel_id, folder_name, folder_type, sort_order) 
-- VALUES (?, '章节', 'chapter', 0);

