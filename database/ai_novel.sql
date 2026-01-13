/*
 Navicat Premium Data Transfer

 Source Server         : local_ai_novel
 Source Server Type    : MySQL
 Source Server Version : 80029
 Source Host           : localhost:3306
 Source Schema         : ai_novel

 Target Server Type    : MySQL
 Target Server Version : 80029
 File Encoding         : 65001

 Date: 01/01/2026 19:21:09
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for ai_adjectives
-- ----------------------------
DROP TABLE IF EXISTS `ai_adjectives`;
CREATE TABLE `ai_adjectives`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `word` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '形容词/可疑词条',
  `lang` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'zh-CN' COMMENT '语言',
  `category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'adjective' COMMENT '类别',
  `hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '去重哈希(可选)',
  `source_model` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '来源模型',
  `created_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_word_lang`(`word`, `lang`) USING BTREE,
  INDEX `idx_category`(`category`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI可疑形容词库' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ai_adjectives_raw
-- ----------------------------
DROP TABLE IF EXISTS `ai_adjectives_raw`;
CREATE TABLE `ai_adjectives_raw`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `word` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '词条',
  `lang` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'zh-CN' COMMENT '语言',
  `category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '类别',
  `source_model` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '来源模型',
  `batch_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '本次采集批次ID',
  `created_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_cat_time`(`category`, `created_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI可疑词条采集明细(不去重)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ai_conversation
-- ----------------------------
DROP TABLE IF EXISTS `ai_conversation`;
CREATE TABLE `ai_conversation`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `novel_id` bigint(0) NOT NULL COMMENT '小说ID',
  `document_id` bigint(0) NULL DEFAULT NULL COMMENT '关联的文档ID',
  `generator_id` bigint(0) NULL DEFAULT NULL COMMENT '使用的生成器ID',
  `user_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '用户输入消息',
  `assistant_message` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT 'AI回复内容',
  `context_data` json NULL COMMENT '上下文数据（参考文件、关联文档等）',
  `word_count` int(0) NULL DEFAULT 0 COMMENT '生成字数',
  `created_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_novel_id`(`novel_id`) USING BTREE,
  INDEX `idx_document_id`(`document_id`) USING BTREE,
  INDEX `idx_created_at`(`created_at`) USING BTREE,
  INDEX `generator_id`(`generator_id`) USING BTREE,
  CONSTRAINT `ai_conversation_ibfk_1` FOREIGN KEY (`novel_id`) REFERENCES `novels` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `ai_conversation_ibfk_2` FOREIGN KEY (`document_id`) REFERENCES `novel_document` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `ai_conversation_ibfk_3` FOREIGN KEY (`generator_id`) REFERENCES `ai_generator` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'AI对话历史表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ai_generator
-- ----------------------------
DROP TABLE IF EXISTS `ai_generator`;
CREATE TABLE `ai_generator`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Tên generator',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'Mô tả ngắn về generator',
  `icon` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'Icon class hoặc tên icon',
  `prompt` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Prompt template cho generator này',
  `category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'writing' COMMENT 'Danh mục: writing, planning, character, etc',
  `sort_order` int(0) NULL DEFAULT 0 COMMENT 'Thứ tự hiển thị',
  `status` tinyint(0) NULL DEFAULT 1 COMMENT '1: active, 0: inactive',
  `created_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'Bảng quản lý các AI Generator' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ai_tasks
-- ----------------------------
DROP TABLE IF EXISTS `ai_tasks`;
CREATE TABLE `ai_tasks`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING',
  `input` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `output` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `error` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `progress_percentage` int(0) NULL DEFAULT 0,
  `estimated_completion` datetime(0) NULL DEFAULT NULL,
  `started_at` datetime(0) NULL DEFAULT NULL,
  `completed_at` datetime(0) NULL DEFAULT NULL,
  `retry_count` int(0) NULL DEFAULT 0,
  `max_retries` int(0) NULL DEFAULT 3,
  `parameters` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `cost_estimate` double NULL DEFAULT NULL,
  `actual_cost` double NULL DEFAULT NULL,
  `user_id` bigint(0) NULL DEFAULT NULL,
  `created_by` bigint(0) NULL DEFAULT NULL,
  `novel_id` bigint(0) NULL DEFAULT NULL,
  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_type`(`type`) USING BTREE,
  INDEX `idx_novel`(`novel_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 314 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for chapter_analysis
-- ----------------------------
DROP TABLE IF EXISTS `chapter_analysis`;
CREATE TABLE `chapter_analysis`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `novel_id` bigint(0) NOT NULL COMMENT '小说ID',
  `analysis_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分析类型：golden_three(黄金三章), main_plot(主线剧情), sub_plot(支线剧情), theme(主题分析), character(角色分析), worldbuilding(世界设定), writing_style(写作风格与技巧)',
  `start_chapter` int(0) NOT NULL COMMENT '开始章节号',
  `end_chapter` int(0) NOT NULL COMMENT '结束章节号',
  `analysis_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '分析内容（Markdown格式）',
  `word_count` int(0) NULL DEFAULT 0 COMMENT '分析内容字数',
  `created_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_novel_id`(`novel_id`) USING BTREE,
  INDEX `idx_analysis_type`(`analysis_type`) USING BTREE,
  INDEX `idx_created_at`(`created_at`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '章节拆解分析表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for chapter_summaries
-- ----------------------------
DROP TABLE IF EXISTS `chapter_summaries`;
CREATE TABLE `chapter_summaries`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `novel_id` bigint(0) NOT NULL,
  `chapter_number` int(0) NOT NULL,
  `summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `created_at` datetime(0) NULL,
  `updated_at` datetime(0) NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_novel_chapter`(`novel_id`, `chapter_number`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1382 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for chapters
-- ----------------------------
DROP TABLE IF EXISTS `chapters`;
CREATE TABLE `chapters`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `subtitle` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `simple_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `order_num` int(0) NOT NULL DEFAULT 0 COMMENT '排序号',
  `status` enum('DRAFT','IN_PROGRESS','WRITING','REVIEW','REVIEWING','PUBLISHED','COMPLETED','ARCHIVED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '章节状态: DRAFT(草稿), IN_PROGRESS(创作中), WRITING(写作中), REVIEW(审核中), REVIEWING(审核中), PUBLISHED(已发布), COMPLETED(已完成), ARCHIVED(已归档)',
  `word_count` int(0) NULL DEFAULT 0,
  `chapter_number` int(0) NULL DEFAULT NULL,
  `summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `notes` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `is_public` tinyint(1) NULL DEFAULT 0,
  `published_at` timestamp(0) NULL DEFAULT NULL,
  `reading_time_minutes` int(0) NULL DEFAULT NULL,
  `previous_chapter_id` bigint(0) NULL DEFAULT NULL,
  `next_chapter_id` bigint(0) NULL DEFAULT NULL,
  `novel_id` bigint(0) NOT NULL,
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  `generation_context` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '写作上下文快照',
  `react_decision_log` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'ReAct决策循环详细日志（工具调用、思考、结果等）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_novel_id`(`novel_id`) USING BTREE,
  INDEX `idx_order_num`(`order_num`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_title`(`title`) USING BTREE,
  CONSTRAINT `chapters_ibfk_1` FOREIGN KEY (`novel_id`) REFERENCES `novels` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 932 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for characters
-- ----------------------------
DROP TABLE IF EXISTS `characters`;
CREATE TABLE `characters`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
  `alias` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '别名',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '人物描述',
  `appearance` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '外貌',
  `personality` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '性格',
  `background` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '背景',
  `motivation` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '动机',
  `goals` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '目标',
  `conflicts` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '冲突',
  `relationships` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '关系网',
  `character_arc` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '角色弧光',
  `is_protagonist` tinyint(1) NULL DEFAULT 0 COMMENT '是否主角',
  `is_antagonist` tinyint(1) NULL DEFAULT 0 COMMENT '是否反派',
  `is_major_character` tinyint(1) NULL DEFAULT 0 COMMENT '是否重要角色',
  `first_appearance_chapter` int(0) NULL DEFAULT NULL COMMENT '首次出现章',
  `last_appearance_chapter` int(0) NULL DEFAULT NULL COMMENT '最近出现章',
  `appearance_count` int(0) NULL DEFAULT 0 COMMENT '出场次数',
  `character_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'MINOR' COMMENT 'PROTAGONIST/MAJOR/MINOR/TEMPORARY',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE/DECEASED/MISSING',
  `importance_score` int(0) NULL DEFAULT 0 COMMENT '重要性评分(0-100)',
  `tags` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '标签',
  `character_image_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '角色头像URL',
  `novel_id` bigint(0) NOT NULL COMMENT '所属小说ID',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_characters_novel_id`(`novel_id`) USING BTREE,
  INDEX `idx_characters_type`(`character_type`) USING BTREE,
  INDEX `idx_characters_updated_at`(`updated_at`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '小说角色表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for foreshadow_lifecycle_log
-- ----------------------------
DROP TABLE IF EXISTS `foreshadow_lifecycle_log`;
CREATE TABLE `foreshadow_lifecycle_log`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `novel_id` bigint(0) NOT NULL COMMENT '小说ID',
  `foreshadow_id` bigint(0) NULL DEFAULT NULL COMMENT '伏笔ID（关联 novel_foreshadowings 表，PLANT时可为NULL，后续回填）',
  `volume_id` bigint(0) NOT NULL COMMENT '卷ID',
  `volume_number` int(0) NOT NULL COMMENT '卷序号',
  `chapter_in_volume` int(0) NOT NULL COMMENT '卷内章节序号',
  `global_chapter_number` int(0) NULL DEFAULT NULL COMMENT '全书章节序号（可选）',
  `action` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '伏笔动作：PLANT（埋）| REFERENCE（提）| DEEPEN（加深）| RESOLVE（揭露）',
  `detail` json NULL COMMENT '详情（JSON对象），包含：\r\n        - content: 伏笔内容（PLANT时必填）\r\n        - targetResolveVolume: 目标揭露卷数（PLANT时可选）\r\n        - resolveWindow: 揭露窗口 {min, max}（PLANT时可选）\r\n        - anchorsUsed: 已使用的证据锚点数组 [{vol, ch, hint}]（RESOLVE时必须≥2个）\r\n        - futureAnchorPlan: 未来证据锚点计划（PLANT/DEEPEN时建议填写）\r\n        - cost: 揭露代价（RESOLVE时可选）\r\n        - autoDowngraded: 是否自动降级（true表示原本想RESOLVE但锚点不足，降级为DEEPEN）',
  `decided_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '决策时间（章纲生成时间）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_foreshadow`(`foreshadow_id`) USING BTREE COMMENT '按伏笔ID查询完整生命周期',
  INDEX `idx_novel_volume`(`novel_id`, `volume_number`) USING BTREE COMMENT '按小说和卷查询',
  INDEX `idx_volume_chapter`(`volume_id`, `chapter_in_volume`) USING BTREE COMMENT '按卷和章节查询',
  INDEX `idx_action`(`action`) USING BTREE COMMENT '按动作类型查询（例如查询所有PLANT/RESOLVE）'
) ENGINE = InnoDB AUTO_INCREMENT = 335 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '伏笔生命周期日志表（跨卷追踪）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for narrative_states
-- ----------------------------
DROP TABLE IF EXISTS `narrative_states`;
CREATE TABLE `narrative_states`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `novel_id` bigint(0) NOT NULL COMMENT '小说ID',
  `volume_id` bigint(0) NULL DEFAULT NULL COMMENT '卷ID',
  `chapter_number` int(0) NOT NULL COMMENT '刚写完的章节号',
  `main_plot_progress` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '主线进度描述',
  `progress_percent` int(0) NULL DEFAULT 0 COMMENT '主线进度百分比',
  `active_threads` json NULL COMMENT '活跃线索/支线',
  `protagonist_state` json NULL COMMENT '主角当前状态',
  `reader_hooks` json NULL COMMENT '读者钩子/悬念',
  `recent_emotions` json NULL COMMENT '最近N章情绪基调',
  `next_direction` json NULL COMMENT '下一步方向建议',
  `anchor_check` json NULL COMMENT '卷级锚点检查结果',
  `rhythm_warning` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '节奏警告',
  `raw_evaluation` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'AI评估原始响应',
  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_novel_chapter`(`novel_id`, `chapter_number`) USING BTREE,
  INDEX `idx_volume`(`volume_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '叙事状态快照' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for novel_document
-- ----------------------------
DROP TABLE IF EXISTS `novel_document`;
CREATE TABLE `novel_document`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `novel_id` bigint(0) NOT NULL COMMENT '小说ID',
  `folder_id` bigint(0) NULL DEFAULT NULL COMMENT '所属文件夹ID',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文档标题',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '文档内容',
  `sort_order` int(0) NULL DEFAULT 0 COMMENT '排序顺序',
  `created_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_novel_id`(`novel_id`) USING BTREE,
  INDEX `idx_folder_id`(`folder_id`) USING BTREE,
  CONSTRAINT `novel_document_ibfk_1` FOREIGN KEY (`novel_id`) REFERENCES `novels` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `novel_document_ibfk_2` FOREIGN KEY (`folder_id`) REFERENCES `novel_folder` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 62 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '小说文档表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for novel_folder
-- ----------------------------
DROP TABLE IF EXISTS `novel_folder`;
CREATE TABLE `novel_folder`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `novel_id` bigint(0) NOT NULL COMMENT '小说ID',
  `folder_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件夹名称',
  `parent_id` bigint(0) NULL DEFAULT NULL COMMENT '父文件夹ID（支持嵌套）',
  `sort_order` int(0) NULL DEFAULT 0 COMMENT '排序顺序',
  `created_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  `is_system` tinyint(1) NULL DEFAULT 0 COMMENT '是否为系统默认文件夹（不可删除、不可重命名）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_novel_id`(`novel_id`) USING BTREE,
  INDEX `idx_parent_id`(`parent_id`) USING BTREE,
  CONSTRAINT `novel_folder_ibfk_1` FOREIGN KEY (`novel_id`) REFERENCES `novels` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 370 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '小说文件夹表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for novel_foreshadowing
-- ----------------------------
DROP TABLE IF EXISTS `novel_foreshadowing`;
CREATE TABLE `novel_foreshadowing`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `novel_id` bigint(0) NOT NULL,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '伏笔内容',
  `planted_chapter` int(0) NOT NULL COMMENT '埋设章节',
  `resolved_chapter` int(0) NULL DEFAULT NULL COMMENT '回收章节',
  `status` enum('ACTIVE','RESOLVED','ABANDONED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'ACTIVE' COMMENT '伏笔状态',
  `type` enum('DEATH','ROMANCE','CONFLICT','MYSTERY','POWER','OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'OTHER' COMMENT '伏笔类型',
  `priority` tinyint(0) NULL DEFAULT 5 COMMENT '优先级1-10',
  `context_info` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '上下文信息',
  `created_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `resolved_time` datetime(0) NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_novel_planted`(`novel_id`, `planted_chapter`) USING BTREE,
  INDEX `idx_status_type`(`status`, `type`) USING BTREE,
  INDEX `idx_priority_status`(`priority`, `status`) USING BTREE,
  CONSTRAINT `novel_foreshadowing_ibfk_1` FOREIGN KEY (`novel_id`) REFERENCES `novels` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '伏笔追踪表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for novel_outlines
-- ----------------------------
DROP TABLE IF EXISTS `novel_outlines`;
CREATE TABLE `novel_outlines`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `novel_id` bigint(0) NOT NULL,
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `genre` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `basic_idea` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `core_theme` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `main_characters` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `plot_structure` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `core_settings` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '核心设定（从大纲提炼，不含具体剧情）',
  `world_setting` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `key_elements` json NULL COMMENT '关键元素列表',
  `conflict_types` json NULL COMMENT '冲突类型列表',
  `target_word_count` int(0) NULL DEFAULT 50000,
  `target_chapter_count` int(0) NULL DEFAULT 20,
  `status` enum('DRAFT','CONFIRMED','REVISED','REVISING') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT',
  `is_ai_generated` tinyint(1) NULL DEFAULT 0,
  `last_modified_by_ai` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `created_by` bigint(0) NULL DEFAULT NULL,
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  `feedback_history` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '反馈历史记录',
  `revision_count` int(0) NULL DEFAULT 0 COMMENT '修订次数',
  `react_decision_log` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'React决策日志，记录大纲生成的提示词和上下文信息',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_novel_id`(`novel_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_created_by`(`created_by`) USING BTREE,
  INDEX `idx_genre`(`genre`) USING BTREE,
  INDEX `idx_core_settings_exists`() USING BTREE,
  CONSTRAINT `novel_outlines_ibfk_1` FOREIGN KEY (`novel_id`) REFERENCES `novels` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `novel_outlines_ibfk_2` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 206 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '小说大纲主表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for novel_template_progress
-- ----------------------------
DROP TABLE IF EXISTS `novel_template_progress`;
CREATE TABLE `novel_template_progress`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `novel_id` bigint(0) NOT NULL COMMENT '关联的小说ID（唯一）',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用模板引擎',
  `current_stage` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MOTIVATION' COMMENT '当前阶段: MOTIVATION/BONUS/CONFRONTATION/RESPONSE/EARNING',
  `loop_number` int(0) NOT NULL DEFAULT 1 COMMENT '当前循环次数（第几轮）',
  `stage_start_chapter` int(0) NOT NULL DEFAULT 1 COMMENT '当前阶段的起始章节号',
  `motivation_analysis` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '动机阶段分析内容',
  `bonus_analysis` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '金手指阶段分析内容',
  `confrontation_analysis` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '装逼/冲突阶段分析内容',
  `response_analysis` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '反馈阶段分析内容',
  `earning_analysis` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '收获阶段分析内容',
  `template_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'GENERAL' COMMENT '模板类型: XUANHUAN/URBAN/SYSTEM/REBIRTH/GENERAL',
  `start_chapter` int(0) NOT NULL DEFAULT 1 COMMENT '模板引擎启动章节（从第几章开始应用）',
  `last_updated_chapter` int(0) NOT NULL DEFAULT 0 COMMENT '最后更新的章节号',
  `created_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `novel_id`(`novel_id`) USING BTREE,
  INDEX `idx_novel_id`(`novel_id`) USING BTREE,
  INDEX `idx_enabled`(`enabled`) USING BTREE,
  INDEX `idx_current_stage`(`current_stage`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '小说模板循环进度表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for novel_volumes
-- ----------------------------
DROP TABLE IF EXISTS `novel_volumes`;
CREATE TABLE `novel_volumes`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `novel_id` bigint(0) NOT NULL,
  `outline_id` bigint(0) NULL DEFAULT NULL,
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `theme` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `content_outline` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `volume_number` int(0) NOT NULL,
  `chapter_start` int(0) NOT NULL,
  `chapter_end` int(0) NOT NULL,
  `estimated_word_count` int(0) NULL DEFAULT 0,
  `actual_word_count` int(0) NULL DEFAULT 0,
  `key_events` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `character_development` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `plot_threads` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `status` enum('PLANNED','IN_PROGRESS','COMPLETED','REVISED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PLANNED',
  `is_ai_generated` tinyint(1) NULL DEFAULT 0,
  `last_modified_by_ai` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `created_by` bigint(0) NULL DEFAULT NULL,
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_novel_id`(`novel_id`) USING BTREE,
  INDEX `idx_outline_id`(`outline_id`) USING BTREE,
  INDEX `idx_volume_number`(`volume_number`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_created_by`(`created_by`) USING BTREE,
  CONSTRAINT `novel_volumes_ibfk_1` FOREIGN KEY (`novel_id`) REFERENCES `novels` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `novel_volumes_ibfk_2` FOREIGN KEY (`outline_id`) REFERENCES `novel_outlines` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `novel_volumes_ibfk_3` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 955 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '小说卷表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for novels
-- ----------------------------
DROP TABLE IF EXISTS `novels`;
CREATE TABLE `novels`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `subtitle` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `cover_image_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `genre` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `tags` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `target_audience` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `estimated_completion` timestamp(0) NULL DEFAULT NULL,
  `started_at` timestamp(0) NULL DEFAULT NULL,
  `completed_at` timestamp(0) NULL DEFAULT NULL,
  `is_public` tinyint(1) NULL DEFAULT 0,
  `rating` decimal(3, 2) NULL DEFAULT 0.00,
  `rating_count` int(0) NULL DEFAULT 0,
  `target_total_chapters` int(0) NULL DEFAULT NULL COMMENT '目标总章数',
  `words_per_chapter` int(0) NULL DEFAULT NULL COMMENT '每章字数',
  `planned_volume_count` int(0) NULL DEFAULT NULL COMMENT '计划卷数',
  `total_word_target` int(0) NULL DEFAULT NULL COMMENT '总字数目标',
  `status` enum('DRAFT','WRITING','REVIEWING','COMPLETED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT',
  `word_count` int(0) NULL DEFAULT 0,
  `chapter_count` int(0) NULL DEFAULT 0,
  `author_id` bigint(0) NOT NULL DEFAULT 1,
  `created_by` bigint(0) NOT NULL,
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  `outline` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '整本书大纲（确认后写入，替代 novel_outlines.plot_structure)',
  `creation_stage` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'OUTLINE_PENDING' COMMENT '创作阶段状态',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_title`(`title`) USING BTREE,
  INDEX `idx_genre`(`genre`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_created_by`(`created_by`) USING BTREE,
  INDEX `idx_created_at`(`created_at`) USING BTREE,
  INDEX `idx_novels_creation_stage`(`creation_stage`) USING BTREE,
  CONSTRAINT `novels_ibfk_1` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 188 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ollama_config
-- ----------------------------
DROP TABLE IF EXISTS `ollama_config`;
CREATE TABLE `ollama_config`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(0) NOT NULL COMMENT '用户ID',
  `enabled` tinyint(1) NULL DEFAULT 0 COMMENT '是否启用Ollama',
  `base_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'http://localhost:11434' COMMENT 'Ollama服务地址',
  `default_model` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '默认模型',
  `fallback_model` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备用模型',
  `max_tokens` int(0) NULL DEFAULT 4096 COMMENT '最大令牌数',
  `temperature` decimal(3, 2) NULL DEFAULT 0.70 COMMENT '温度参数',
  `top_p` decimal(3, 2) NULL DEFAULT 0.90 COMMENT 'Top-P参数',
  `connection_timeout` int(0) NULL DEFAULT 30 COMMENT '连接超时时间(秒)',
  `enable_fallback` tinyint(1) NULL DEFAULT 1 COMMENT '是否启用备用服务',
  `fallback_provider` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'openai' COMMENT '备用服务提供商',
  `created_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_config`(`user_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_enabled`(`enabled`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Ollama配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for prompt_template_favorites
-- ----------------------------
DROP TABLE IF EXISTS `prompt_template_favorites`;
CREATE TABLE `prompt_template_favorites`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(0) NOT NULL COMMENT '用户ID',
  `template_id` bigint(0) NOT NULL COMMENT '模板ID',
  `created_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_template`(`user_id`, `template_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_template_id`(`template_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '提示词模板收藏表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for prompt_templates
-- ----------------------------
DROP TABLE IF EXISTS `prompt_templates`;
CREATE TABLE `prompt_templates`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模板名称',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '提示词内容',
  `type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'custom' COMMENT '模板类型：official-官方，custom-用户自定义',
  `user_id` bigint(0) NULL DEFAULT NULL COMMENT '用户ID（官方模板为NULL）',
  `category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '分类：system_identity-系统身份，writing_style-写作风格，anti_ai-去AI味，outline-大纲生成',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模板描述',
  `is_active` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用：1-启用，0-禁用',
  `is_default` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否默认：1-默认，0-非默认',
  `usage_count` int(0) NOT NULL DEFAULT 0 COMMENT '使用次数',
  `created_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_type`(`type`) USING BTREE,
  INDEX `idx_category`(`category`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 18 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '提示词模板表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for qimao_categories
-- ----------------------------
DROP TABLE IF EXISTS `qimao_categories`;
CREATE TABLE `qimao_categories`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `category_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类名称',
  `category_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类代码',
  `category_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类URL',
  `parent_category` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '父分类',
  `sort_order` int(0) NULL DEFAULT 0 COMMENT '排序',
  `is_active` tinyint(1) NULL DEFAULT 1 COMMENT '是否启用',
  `last_scrape_time` timestamp(0) NULL DEFAULT NULL COMMENT '最后爬取时间',
  `scrape_count` int(0) NULL DEFAULT 0 COMMENT '爬取次数',
  `created_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_category_code`(`category_code`) USING BTREE,
  INDEX `idx_parent_category`(`parent_category`) USING BTREE,
  INDEX `idx_last_scrape_time`(`last_scrape_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '七猫分类配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for qimao_novels
-- ----------------------------
DROP TABLE IF EXISTS `qimao_novels`;
CREATE TABLE `qimao_novels`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `novel_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '小说唯一标识',
  `title` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '小说标题',
  `author` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '作者',
  `category` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '分类（如：总裁豪门、古言种田等）',
  `sub_category` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '子分类',
  `tags` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '标签（JSON数组格式）',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '小说简介',
  `word_count` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '字数',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '状态（连载中/完结）',
  `update_time` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新时间',
  `first_chapter_title` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '第一章标题',
  `first_chapter_content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '第一章内容',
  `novel_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '小说链接',
  `author_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '作者链接',
  `cover_image_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '封面图片链接',
  `rank_position` int(0) NULL DEFAULT NULL COMMENT '排行榜位置',
  `rank_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '排行榜类型（点击/收藏/推荐等）',
  `created_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `novel_id`(`novel_id`) USING BTREE,
  INDEX `idx_category`(`category`) USING BTREE,
  INDEX `idx_author`(`author`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_rank_type`(`rank_type`) USING BTREE,
  INDEX `idx_update_time`(`update_time`) USING BTREE,
  INDEX `idx_created_at`(`created_at`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 95 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '七猫小说爬取数据表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for qimao_scraper_config
-- ----------------------------
DROP TABLE IF EXISTS `qimao_scraper_config`;
CREATE TABLE `qimao_scraper_config`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置键',
  `config_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '配置值',
  `description` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '配置描述',
  `created_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `config_key`(`config_key`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 8 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '爬虫配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for qimao_scraper_tasks
-- ----------------------------
DROP TABLE IF EXISTS `qimao_scraper_tasks`;
CREATE TABLE `qimao_scraper_tasks`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务名称',
  `category_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '分类代码',
  `task_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT '任务状态（PENDING/RUNNING/COMPLETED/FAILED）',
  `total_novels` int(0) NULL DEFAULT 0 COMMENT '总小说数',
  `success_count` int(0) NULL DEFAULT 0 COMMENT '成功数',
  `failed_count` int(0) NULL DEFAULT 0 COMMENT '失败数',
  `start_time` timestamp(0) NULL DEFAULT NULL COMMENT '开始时间',
  `end_time` timestamp(0) NULL DEFAULT NULL COMMENT '结束时间',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '错误信息',
  `created_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_task_status`(`task_status`) USING BTREE,
  INDEX `idx_category_code`(`category_code`) USING BTREE,
  INDEX `idx_created_at`(`created_at`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 11 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '爬虫任务记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for reference_file
-- ----------------------------
DROP TABLE IF EXISTS `reference_file`;
CREATE TABLE `reference_file`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `novel_id` bigint(0) NOT NULL COMMENT '小说ID',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件名',
  `file_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件类型: txt/docx',
  `file_content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '提取的文本内容',
  `file_size` bigint(0) NULL DEFAULT 0 COMMENT '文件大小（字节）',
  `original_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '原文件存储路径',
  `word_count` int(0) NULL DEFAULT 0 COMMENT '字数统计',
  `created_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_novel_id`(`novel_id`) USING BTREE,
  CONSTRAINT `reference_file_ibfk_1` FOREIGN KEY (`novel_id`) REFERENCES `novels` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '参考文件表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for roles
-- ----------------------------
DROP TABLE IF EXISTS `roles`;
CREATE TABLE `roles`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `permissions` json NULL,
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `name`(`name`) USING BTREE,
  INDEX `idx_name`(`name`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for rolling_outlines
-- ----------------------------
DROP TABLE IF EXISTS `rolling_outlines`;
CREATE TABLE `rolling_outlines`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `novel_id` bigint(0) NOT NULL COMMENT '小说ID',
  `volume_id` bigint(0) NULL DEFAULT NULL COMMENT '卷ID',
  `chapter_number` int(0) NOT NULL COMMENT '章节号',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PLANNED' COMMENT '状态：PLANNED/WRITING/WRITTEN/ADJUSTED',
  `chapter_goals` json NULL COMMENT '章节目标（目标驱动）',
  `constraints` json NULL COMMENT '约束条件',
  `flexibility` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'medium' COMMENT '灵活度：high/medium/low',
  `suggested_direction` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '建议的剧情方向',
  `actual_summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '实际写出的内容摘要',
  `actual_emotion` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '实际情绪基调',
  `deviation_note` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '与原计划的偏离说明',
  `narrative_state_id` bigint(0) NULL DEFAULT NULL COMMENT '生成此章纲时的叙事状态ID',
  `batch_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '滚动批次号',
  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_novel_chapter`(`novel_id`, `chapter_number`) USING BTREE,
  INDEX `idx_status`(`novel_id`, `status`) USING BTREE,
  INDEX `idx_batch`(`batch_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '滚动章纲' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user_roles
-- ----------------------------
DROP TABLE IF EXISTS `user_roles`;
CREATE TABLE `user_roles`  (
  `user_id` bigint(0) NOT NULL,
  `role_id` bigint(0) NOT NULL,
  PRIMARY KEY (`user_id`, `role_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_role_id`(`role_id`) USING BTREE,
  CONSTRAINT `user_roles_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `user_roles_ibfk_2` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'USER' COMMENT '用户角色: USER/ADMIN',
  `nickname` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `avatar_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `bio` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `status` enum('ACTIVE','INACTIVE','BANNED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `last_login_at` timestamp(0) NULL DEFAULT NULL,
  `email_verified` tinyint(1) NULL DEFAULT 0,
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `username`(`username`) USING BTREE,
  UNIQUE INDEX `email`(`email`) USING BTREE,
  INDEX `idx_username`(`username`) USING BTREE,
  INDEX `idx_email`(`email`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 11 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for volume_anchors
-- ----------------------------
DROP TABLE IF EXISTS `volume_anchors`;
CREATE TABLE `volume_anchors`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `novel_id` bigint(0) NOT NULL COMMENT '小说ID',
  `volume_id` bigint(0) NOT NULL COMMENT '卷ID',
  `event` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '锚点事件描述',
  `deadline_chapter` int(0) NULL DEFAULT NULL COMMENT '截止章节',
  `priority` int(0) NULL DEFAULT 1 COMMENT '优先级',
  `is_completed` tinyint(1) NULL DEFAULT 0 COMMENT '是否已完成',
  `completed_chapter` int(0) NULL DEFAULT NULL COMMENT '完成章节号',
  `anchor_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PLOT' COMMENT '锚点类型：PLOT/CHARACTER/FORESHADOW/CLIMAX',
  `prerequisite_anchor_id` bigint(0) NULL DEFAULT NULL COMMENT '前置锚点ID',
  `notes` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '备注',
  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_volume`(`volume_id`) USING BTREE,
  INDEX `idx_novel_pending`(`novel_id`, `is_completed`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '卷级锚点' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for volume_chapter_outlines
-- ----------------------------
DROP TABLE IF EXISTS `volume_chapter_outlines`;
CREATE TABLE `volume_chapter_outlines`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `novel_id` bigint(0) NOT NULL COMMENT '小说ID，关联 novels 表',
  `volume_id` bigint(0) NOT NULL COMMENT '卷ID，关联 novel_volumes 表',
  `volume_number` int(0) NOT NULL COMMENT '卷序号（冗余字段，便于查询）',
  `chapter_in_volume` int(0) NOT NULL COMMENT '卷内章节序号（从1开始）',
  `global_chapter_number` int(0) NULL DEFAULT NULL COMMENT '全书章节序号（从1开始，可选，若卷未设置起始章节则为NULL）',
  `direction` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '本章剧情方向（1句话，例如：\"主角在拍卖会上竞拍神秘丹药，引发多方势力暗中角力\"）',
  `key_plot_points` json NULL COMMENT '关键剧情点（JSON数组，例如：[\"拍卖会开场，主角低调入场\",\"神秘丹药出现，引发哄抢\",\"主角出价，暴露部分实力\"]）',
  `emotional_tone` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '情感基调（例如：\"紧张、期待、暗流涌动\"）',
  `foreshadow_action` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'NONE' COMMENT '伏笔动作：NONE（无）| PLANT（埋）| REFERENCE（提）| DEEPEN（加深）| RESOLVE（揭露）',
  `foreshadow_detail` json NULL COMMENT '伏笔详情（JSON对象），包含：\r\n        - refId: 引用的伏笔ID（REFERENCE/DEEPEN/RESOLVE时必填）\r\n        - content: 伏笔内容（PLANT时必填）\r\n        - targetResolveVolume: 目标揭露卷数（PLANT时可选）\r\n        - resolveWindow: 揭露窗口 {min, max}（PLANT时可选）\r\n        - anchorsUsed: 已使用的证据锚点数组 [{vol, ch, hint}]（RESOLVE时必须≥2个）\r\n        - futureAnchorPlan: 未来证据锚点计划（PLANT/DEEPEN时建议填写）\r\n        - cost: 揭露代价（RESOLVE时可选，例如：\"主角身份暴露，引发追杀\"）',
  `subplot` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '支线剧情（例如：\"女主角暗中调查主角身份\"）',
  `antagonism` json NULL COMMENT '对抗关系（JSON对象），包含：\r\n        - opponent: 对手名称\r\n        - conflictType: 冲突类型（利益/理念/情感/生存等）\r\n        - intensity: 强度（1-10）',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'PENDING' COMMENT '章节状态：PENDING（待写作）| WRITTEN（已写作）| REVISED（已修订）',
  `react_decision_log` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'AI决策日志（生成章纲时的推理过程、提示词、上下文等，JSON格式，便于审计和调试）',
  `created_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_novel_volume`(`novel_id`, `volume_number`) USING BTREE COMMENT '按小说和卷查询',
  INDEX `idx_volume_chapter`(`volume_id`, `chapter_in_volume`) USING BTREE COMMENT '按卷和卷内章节查询',
  INDEX `idx_global_chapter`(`novel_id`, `global_chapter_number`) USING BTREE COMMENT '按全书章节号查询（写作时优先查询预生成章纲）',
  INDEX `idx_status`(`status`) USING BTREE COMMENT '按状态查询（例如查询所有待写作章节）',
  INDEX `idx_foreshadow_action`(`foreshadow_action`) USING BTREE COMMENT '按伏笔动作查询（例如查询所有待揭露伏笔）'
) ENGINE = InnoDB AUTO_INCREMENT = 6249 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '卷级章节大纲表（按卷批量预生成）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for writing_version_history
-- ----------------------------
DROP TABLE IF EXISTS `writing_version_history`;
CREATE TABLE `writing_version_history`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '版本记录ID',
  `novel_id` bigint(0) NOT NULL COMMENT '小说ID',
  `chapter_id` bigint(0) NULL DEFAULT NULL COMMENT '章节ID（章节模式时使用）',
  `document_id` bigint(0) NULL DEFAULT NULL COMMENT '文档ID（辅助文档模式时使用）',
  `source_type` enum('AUTO_SAVE','MANUAL_SAVE','AI_REPLACE') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'AUTO_SAVE' COMMENT '来源：自动保存/手动保存/AI 替换正文',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '完整内容快照',
  `word_count` int(0) NOT NULL DEFAULT 0 COMMENT '字数统计（不含空白）',
  `diff_ratio` decimal(5, 2) NULL DEFAULT NULL COMMENT '相对于上一版本的差异百分比（0-100）',
  `created_at` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_novel_chapter`(`novel_id`, `chapter_id`) USING BTREE,
  INDEX `idx_novel_document`(`novel_id`, `document_id`) USING BTREE,
  INDEX `idx_created_at`(`created_at`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1362 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '写作内容版本历史' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
