-- ============================================================================
-- 卷级章纲批量生成与伏笔生命周期管理 - 数据库表结构
-- ============================================================================
-- 功能说明:
-- 1. volume_chapter_outlines: 存储按卷批量预生成的章节大纲
-- 2. foreshadow_lifecycle_log: 记录伏笔的完整生命周期（PLANT/REFERENCE/DEEPEN/RESOLVE）
--
-- 设计理念:
-- - 预生成章纲避免写作时的故事漂移，确保全卷节奏一致
-- - 伏笔生命周期日志支持跨卷追踪，确保"揭露有据、不突兀"
-- - 证据锚点机制（anchorsUsed）保证任何 RESOLVE 都能引用前文具体证据
-- ============================================================================

-- ============================================================================
-- 表1: volume_chapter_outlines (卷级章节大纲表)
-- ============================================================================
-- 用途: 存储按卷批量生成的章节大纲，每章一行
-- 核心字段:
--   - direction: 本章剧情方向（1句话）
--   - key_plot_points: 关键剧情点（JSON数组）
--   - emotional_tone: 情感基调
--   - foreshadow_action: 伏笔动作（NONE/PLANT/REFERENCE/DEEPEN/RESOLVE）
--   - foreshadow_detail: 伏笔详情（JSON对象，含 anchorsUsed/futureAnchorPlan 等）
--   - subplot: 支线剧情
--   - antagonism: 对抗关系（JSON对象）
--   - status: 章节状态（PENDING/WRITTEN/REVISED）
--   - react_decision_log: AI决策日志（生成章纲时的推理过程、提示词等，便于审计）
-- ============================================================================
CREATE TABLE IF NOT EXISTS volume_chapter_outlines (
    -- 主键
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    
    -- 关联字段
    novel_id BIGINT NOT NULL COMMENT '小说ID，关联 novels 表',
    volume_id BIGINT NOT NULL COMMENT '卷ID，关联 novel_volumes 表',
    volume_number INT NOT NULL COMMENT '卷序号（冗余字段，便于查询）',
    
    -- 章节定位
    chapter_in_volume INT NOT NULL COMMENT '卷内章节序号（从1开始）',
    global_chapter_number INT NULL COMMENT '全书章节序号（从1开始，可选，若卷未设置起始章节则为NULL）',

    -- 章纲核心内容
    direction TEXT COMMENT '本章剧情方向（1句话，例如："主角在拍卖会上竞拍神秘丹药，引发多方势力暗中角力"）',
    key_plot_points JSON COMMENT '关键剧情点（JSON数组，例如：["拍卖会开场，主角低调入场","神秘丹药出现，引发哄抢","主角出价，暴露部分实力"]）',
    emotional_tone VARCHAR(100) COMMENT '情感基调（例如："紧张、期待、暗流涌动"）',
    
    -- 伏笔管理
    foreshadow_action VARCHAR(20) DEFAULT 'NONE' COMMENT '伏笔动作：NONE（无）| PLANT（埋）| REFERENCE（提）| DEEPEN（加深）| RESOLVE（揭露）',
    foreshadow_detail JSON COMMENT '伏笔详情（JSON对象），包含：
        - refId: 引用的伏笔ID（REFERENCE/DEEPEN/RESOLVE时必填）
        - content: 伏笔内容（PLANT时必填）
        - targetResolveVolume: 目标揭露卷数（PLANT时可选）
        - resolveWindow: 揭露窗口 {min, max}（PLANT时可选）
        - anchorsUsed: 已使用的证据锚点数组 [{vol, ch, hint}]（RESOLVE时必须≥2个）
        - futureAnchorPlan: 未来证据锚点计划（PLANT/DEEPEN时建议填写）
        - cost: 揭露代价（RESOLVE时可选，例如："主角身份暴露，引发追杀"）',
    
    -- 支线与对抗
    subplot TEXT COMMENT '支线剧情（例如："女主角暗中调查主角身份"）',
    antagonism JSON COMMENT '对抗关系（JSON对象），包含：
        - opponent: 对手名称
        - conflictType: 冲突类型（利益/理念/情感/生存等）
        - intensity: 强度（1-10）',
    
    -- 状态管理
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '章节状态：PENDING（待写作）| WRITTEN（已写作）| REVISED（已修订）',
    
    -- 审计与调试
    react_decision_log LONGTEXT COMMENT 'AI决策日志（生成章纲时的推理过程、提示词、上下文等，JSON格式，便于审计和调试）',
    
    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    -- 索引
    INDEX idx_novel_volume (novel_id, volume_number) COMMENT '按小说和卷查询',
    INDEX idx_volume_chapter (volume_id, chapter_in_volume) COMMENT '按卷和卷内章节查询',
    INDEX idx_global_chapter (novel_id, global_chapter_number) COMMENT '按全书章节号查询（写作时优先查询预生成章纲）',
    INDEX idx_status (status) COMMENT '按状态查询（例如查询所有待写作章节）',
    INDEX idx_foreshadow_action (foreshadow_action) COMMENT '按伏笔动作查询（例如查询所有待揭露伏笔）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卷级章节大纲表（按卷批量预生成）';


-- ============================================================================
-- 表2: foreshadow_lifecycle_log (伏笔生命周期日志表)
-- ============================================================================
-- 用途: 记录伏笔的完整生命周期，支持跨卷追踪
-- 核心字段:
--   - foreshadow_id: 伏笔ID（关联 novel_foreshadowings 表，可为NULL表示新埋伏笔）
--   - action: 动作类型（PLANT/REFERENCE/DEEPEN/RESOLVE）
--   - detail: 详情（JSON对象，含 anchorsUsed/futureAnchorPlan/cost 等）
-- 设计理念:
--   - 每次章纲生成时，若涉及伏笔操作，就写入一条日志
--   - 下一卷生成章纲前，汇总 ACTIVE 伏笔 + anchors，作为上下文输入
--   - 支持"证据锚点机制"：RESOLVE 必须引用≥2个 anchors，否则自动降级为 DEEPEN
-- ============================================================================
CREATE TABLE IF NOT EXISTS foreshadow_lifecycle_log (
    -- 主键
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    
    -- 关联字段
    novel_id BIGINT NOT NULL COMMENT '小说ID',
    foreshadow_id BIGINT COMMENT '伏笔ID（关联 novel_foreshadowings 表，PLANT时可为NULL，后续回填）',
    
    -- 定位字段
    volume_id BIGINT NOT NULL COMMENT '卷ID',
    volume_number INT NOT NULL COMMENT '卷序号',
    chapter_in_volume INT NOT NULL COMMENT '卷内章节序号',
    global_chapter_number INT NULL COMMENT '全书章节序号（可选）',
    
    -- 动作与详情
    action VARCHAR(20) NOT NULL COMMENT '伏笔动作：PLANT（埋）| REFERENCE（提）| DEEPEN（加深）| RESOLVE（揭露）',
    detail JSON COMMENT '详情（JSON对象），包含：
        - content: 伏笔内容（PLANT时必填）
        - targetResolveVolume: 目标揭露卷数（PLANT时可选）
        - resolveWindow: 揭露窗口 {min, max}（PLANT时可选）
        - anchorsUsed: 已使用的证据锚点数组 [{vol, ch, hint}]（RESOLVE时必须≥2个）
        - futureAnchorPlan: 未来证据锚点计划（PLANT/DEEPEN时建议填写）
        - cost: 揭露代价（RESOLVE时可选）
        - autoDowngraded: 是否自动降级（true表示原本想RESOLVE但锚点不足，降级为DEEPEN）',
    
    -- 时间戳
    decided_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '决策时间（章纲生成时间）',
    
    -- 索引
    INDEX idx_foreshadow (foreshadow_id) COMMENT '按伏笔ID查询完整生命周期',
    INDEX idx_novel_volume (novel_id, volume_number) COMMENT '按小说和卷查询',
    INDEX idx_volume_chapter (volume_id, chapter_in_volume) COMMENT '按卷和章节查询',
    INDEX idx_action (action) COMMENT '按动作类型查询（例如查询所有PLANT/RESOLVE）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='伏笔生命周期日志表（跨卷追踪）';


-- ============================================================================
-- 使用说明
-- ============================================================================
-- 1. 生成卷级章纲时：
--    - 调用 POST /api/volumes/{volumeId}/chapter-outlines/generate
--    - 后端自动写入 volume_chapter_outlines 和 foreshadow_lifecycle_log
--
-- 2. 写作章节时：
--    - AgenticChapterWriter 优先查询 volume_chapter_outlines（按 novel_id + global_chapter_number）
--    - 若存在预生成章纲，跳过推理，直接用章纲 + 上下文（图谱/概要/卷蓝图）写作
--    - 若不存在章纲，回退到实时推理（兜底）
--
-- 3. 生成下一卷章纲时：
--    - 从 foreshadow_lifecycle_log 汇总 ACTIVE 伏笔（未 RESOLVE 的）
--    - 提取所有 anchors（证据锚点），作为上下文输入
--    - AI 自主判断本卷是否揭露、加深或只是提一提
--    - 若要 RESOLVE，必须引用≥2个 anchors，否则自动降级为 DEEPEN
--
-- 4. 查询示例：
--    -- 查询某卷所有章纲
--    SELECT * FROM volume_chapter_outlines WHERE volume_id = ? ORDER BY chapter_in_volume;
--
--    -- 查询某章预生成章纲（写作时优先查询）
--    SELECT * FROM volume_chapter_outlines WHERE novel_id = ? AND global_chapter_number = ?;
--
--    -- 查询某伏笔的完整生命周期
--    SELECT * FROM foreshadow_lifecycle_log WHERE foreshadow_id = ? ORDER BY decided_at;
--
--    -- 查询某卷所有伏笔操作
--    SELECT * FROM foreshadow_lifecycle_log WHERE volume_id = ? ORDER BY chapter_in_volume;
--
--    -- 查询所有待揭露伏笔（ACTIVE状态）
--    SELECT DISTINCT foreshadow_id FROM foreshadow_lifecycle_log 
--    WHERE action IN ('PLANT', 'REFERENCE', 'DEEPEN') 
--      AND foreshadow_id NOT IN (SELECT foreshadow_id FROM foreshadow_lifecycle_log WHERE action = 'RESOLVE')
--    ORDER BY foreshadow_id;
-- ============================================================================

-- ============================================================================
-- 数据迁移与兼容性说明
-- ============================================================================
-- 1. 本表结构与现有 novel_foreshadowings 表兼容，不冲突
-- 2. foreshadow_lifecycle_log.foreshadow_id 可关联 novel_foreshadowings.id
-- 3. 若需要，可在 novel_foreshadowings 表中新增 resolve_window_min/max 字段
-- 4. 本表使用 JSON 字段存储复杂结构，需 MySQL 5.7.8+ 或 MariaDB 10.2.7+
-- ============================================================================

-- ============================================================================
-- 性能优化建议
-- ============================================================================
-- 1. 若小说章节数>1000，建议对 global_chapter_number 建立分区表
-- 2. foreshadow_lifecycle_log 表会随着卷数增长，建议定期归档已完结小说的日志
-- 3. JSON 字段查询性能较低，若需高频查询 anchorsUsed，可考虑提取为独立表
-- ============================================================================

-- ============================================================================
-- 数据库修复脚本（针对已创建的表）
-- ============================================================================
-- 如果你的数据库中 global_chapter_number 字段是 NOT NULL，需要执行以下语句修复：

-- 修复 volume_chapter_outlines 表
ALTER TABLE volume_chapter_outlines
MODIFY COLUMN global_chapter_number INT NULL COMMENT '全书章节序号（从1开始，可选，若卷未设置起始章节则为NULL）';

-- 修复 foreshadow_lifecycle_log 表
ALTER TABLE foreshadow_lifecycle_log
MODIFY COLUMN global_chapter_number INT NULL COMMENT '全书章节序号（可选）';

-- ============================================================================
