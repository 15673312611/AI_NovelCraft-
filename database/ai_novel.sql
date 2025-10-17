/*
 Navicat Premium Data Transfer

 Source Server         : localhost
 Source Server Type    : MySQL
 Source Server Version : 80035
 Source Host           : localhost:3306
 Source Schema         : ai_novel

 Target Server Type    : MySQL
 Target Server Version : 80035
 File Encoding         : 65001

 Date: 27/09/2025 18:10:24
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

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
  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0),
  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_type`(`type`) USING BTREE,
  INDEX `idx_novel`(`novel_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 34 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for chapter_plans
-- ----------------------------
DROP TABLE IF EXISTS `chapter_plans`;
CREATE TABLE `chapter_plans`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `chapter_number` int(0) NOT NULL,
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `phase` enum('opening','development','climax','resolution') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `main_goal` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `key_events` json NULL COMMENT '关键事件列表',
  `character_focus` json NULL COMMENT '重点角色列表',
  `plot_threads` json NULL COMMENT '情节线列表',
  `estimated_word_count` int(0) NULL DEFAULT 2000,
  `priority` enum('high','medium','low') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'medium',
  `status` enum('planned','in_progress','completed') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'planned',
  `novel_id` bigint(0) NOT NULL,
  `created_by` bigint(0) NULL DEFAULT NULL,
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `created_by`(`created_by`) USING BTREE,
  INDEX `idx_novel_id`(`novel_id`) USING BTREE,
  INDEX `idx_chapter_number`(`chapter_number`) USING BTREE,
  INDEX `idx_phase`(`phase`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_priority`(`priority`) USING BTREE,
  CONSTRAINT `chapter_plans_ibfk_1` FOREIGN KEY (`novel_id`) REFERENCES `novels` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `chapter_plans_ibfk_2` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '章节规划表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for chapter_summaries
-- ----------------------------
DROP TABLE IF EXISTS `chapter_summaries`;
CREATE TABLE `chapter_summaries`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `novel_id` bigint(0) NOT NULL,
  `chapter_number` int(0) NOT NULL,
  `summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `created_at` datetime(0) NOT NULL,
  `updated_at` datetime(0) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_novel_chapter`(`novel_id`, `chapter_number`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

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
  `status` enum('DRAFT','WRITING','REVIEWING','COMPLETED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT',
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
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_novel_id`(`novel_id`) USING BTREE,
  INDEX `idx_order_num`(`order_num`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_title`(`title`) USING BTREE,
  CONSTRAINT `chapters_ibfk_1` FOREIGN KEY (`novel_id`) REFERENCES `novels` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

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
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_characters_novel_id`(`novel_id`) USING BTREE,
  INDEX `idx_characters_type`(`character_type`) USING BTREE,
  INDEX `idx_characters_updated_at`(`updated_at`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '小说角色表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for novel_character_profiles
-- ----------------------------
DROP TABLE IF EXISTS `novel_character_profiles`;
CREATE TABLE `novel_character_profiles`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `novel_id` bigint(0) NOT NULL,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色姓名',
  `first_appearance` int(0) NOT NULL COMMENT '首次出现章节',
  `last_appearance` int(0) NOT NULL COMMENT '最后出现章节',
  `appearance_count` int(0) NULL DEFAULT 1 COMMENT '出现次数',
  `status` enum('ACTIVE','DEAD','INJURED','ABSENT') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'ACTIVE' COMMENT '角色状态',
  `status_change_chapter` int(0) NULL DEFAULT NULL COMMENT '状态改变章节',
  `personality_traits` json NULL COMMENT '性格特征',
  `key_events` json NULL COMMENT '关键事件',
  `relationships` json NULL COMMENT '人际关系',
  `actions_history` json NULL COMMENT '行为历史',
  `importance_score` int(0) NULL DEFAULT 50 COMMENT '重要性评分',
  `created_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0),
  `updated_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_novel_character`(`novel_id`, `name`) USING BTREE,
  INDEX `idx_appearance`(`last_appearance`) USING BTREE,
  INDEX `idx_status_appearance`(`status`, `last_appearance`) USING BTREE,
  CONSTRAINT `novel_character_profiles_ibfk_1` FOREIGN KEY (`novel_id`) REFERENCES `novels` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色档案表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for novel_chronicle
-- ----------------------------
DROP TABLE IF EXISTS `novel_chronicle`;
CREATE TABLE `novel_chronicle`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `novel_id` bigint(0) NOT NULL,
  `chapter_number` int(0) NOT NULL COMMENT '章节号',
  `events` json NOT NULL COMMENT '事件列表',
  `timeline_info` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '时间线信息',
  `event_type` enum('DEATH','MARRIAGE','BREAKTHROUGH','BATTLE','DISCOVERY','TRAVEL','DECISION') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '事件类型',
  `importance_level` tinyint(0) NULL DEFAULT 5 COMMENT '重要程度1-10',
  `created_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_novel_chapter`(`novel_id`, `chapter_number`) USING BTREE,
  INDEX `idx_event_type`(`event_type`) USING BTREE,
  INDEX `idx_importance_chapter`(`importance_level`, `chapter_number`) USING BTREE,
  CONSTRAINT `novel_chronicle_ibfk_1` FOREIGN KEY (`novel_id`) REFERENCES `novels` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '大事年表' ROW_FORMAT = Dynamic;

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
  `created_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0),
  `resolved_time` datetime(0) NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_novel_planted`(`novel_id`, `planted_chapter`) USING BTREE,
  INDEX `idx_status_type`(`status`, `type`) USING BTREE,
  INDEX `idx_priority_status`(`priority`, `status`) USING BTREE,
  CONSTRAINT `novel_foreshadowing_ibfk_1` FOREIGN KEY (`novel_id`) REFERENCES `novels` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '伏笔追踪表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for novel_memory_versions
-- ----------------------------
DROP TABLE IF EXISTS `novel_memory_versions`;
CREATE TABLE `novel_memory_versions`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `novel_id` bigint(0) NOT NULL,
  `version_number` int(0) NOT NULL COMMENT '版本号',
  `last_updated_chapter` int(0) NOT NULL COMMENT '最后更新章节',
  `character_count` int(0) NULL DEFAULT 0 COMMENT '角色数量',
  `event_count` int(0) NULL DEFAULT 0 COMMENT '事件数量',
  `foreshadowing_count` int(0) NULL DEFAULT 0 COMMENT '伏笔数量',
  `term_count` int(0) NULL DEFAULT 0 COMMENT '词条数量',
  `conflict_warnings` json NULL COMMENT '冲突警告',
  `memory_snapshot` json NULL COMMENT '记忆快照',
  `created_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_novel_version`(`novel_id`, `version_number`) USING BTREE,
  CONSTRAINT `novel_memory_versions_ibfk_1` FOREIGN KEY (`novel_id`) REFERENCES `novels` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '记忆库版本管理' ROW_FORMAT = Dynamic;

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
  `world_setting` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `key_elements` json NULL COMMENT '关键元素列表',
  `conflict_types` json NULL COMMENT '冲突类型列表',
  `target_word_count` int(0) NULL DEFAULT 50000,
  `target_chapter_count` int(0) NULL DEFAULT 20,
  `status` enum('DRAFT','CONFIRMED','REVISED','REVISING') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT',
  `is_ai_generated` tinyint(1) NULL DEFAULT 0,
  `last_modified_by_ai` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `created_by` bigint(0) NULL DEFAULT NULL,
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  `feedback_history` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '反馈历史记录',
  `revision_count` int(0) NULL DEFAULT 0 COMMENT '修订次数',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_novel_id`(`novel_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_created_by`(`created_by`) USING BTREE,
  INDEX `idx_genre`(`genre`) USING BTREE,
  CONSTRAINT `novel_outlines_ibfk_1` FOREIGN KEY (`novel_id`) REFERENCES `novels` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `novel_outlines_ibfk_2` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 31 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '小说大纲主表' ROW_FORMAT = Dynamic;

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
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_novel_id`(`novel_id`) USING BTREE,
  INDEX `idx_outline_id`(`outline_id`) USING BTREE,
  INDEX `idx_volume_number`(`volume_number`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_created_by`(`created_by`) USING BTREE,
  CONSTRAINT `novel_volumes_ibfk_1` FOREIGN KEY (`novel_id`) REFERENCES `novels` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `novel_volumes_ibfk_2` FOREIGN KEY (`outline_id`) REFERENCES `novel_outlines` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `novel_volumes_ibfk_3` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 129 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '小说卷表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for novel_world_dictionary
-- ----------------------------
DROP TABLE IF EXISTS `novel_world_dictionary`;
CREATE TABLE `novel_world_dictionary`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `novel_id` bigint(0) NOT NULL,
  `term` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '词条',
  `type` enum('GEOGRAPHY','POWER_SYSTEM','ORGANIZATION','ITEM','CONCEPT') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '词条类型',
  `first_mention` int(0) NOT NULL COMMENT '首次出现章节',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '描述信息',
  `context_info` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '上下文',
  `usage_count` int(0) NULL DEFAULT 1 COMMENT '使用次数',
  `is_important` tinyint(1) NULL DEFAULT 0 COMMENT '是否重要设定',
  `created_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0),
  `updated_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_novel_term`(`novel_id`, `term`) USING BTREE,
  INDEX `idx_type_important`(`type`, `is_important`) USING BTREE,
  INDEX `idx_usage_important`(`usage_count`, `is_important`) USING BTREE,
  CONSTRAINT `novel_world_dictionary_ibfk_1` FOREIGN KEY (`novel_id`) REFERENCES `novels` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '世界观词典' ROW_FORMAT = Dynamic;

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
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
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
) ENGINE = InnoDB AUTO_INCREMENT = 32 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for outline_details
-- ----------------------------
DROP TABLE IF EXISTS `outline_details`;
CREATE TABLE `outline_details`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `outline_id` bigint(0) NOT NULL,
  `section_type` enum('theme','characters','plot_structure','world_setting','key_elements','conflicts') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `section_data` json NOT NULL COMMENT '章节详细数据',
  `order_num` int(0) NULL DEFAULT 0,
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_outline_id`(`outline_id`) USING BTREE,
  INDEX `idx_section_type`(`section_type`) USING BTREE,
  INDEX `idx_order_num`(`order_num`) USING BTREE,
  CONSTRAINT `outline_details_ibfk_1` FOREIGN KEY (`outline_id`) REFERENCES `novel_outlines` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '大纲详细内容表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for plot_points
-- ----------------------------
DROP TABLE IF EXISTS `plot_points`;
CREATE TABLE `plot_points`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` enum('INCITING_INCIDENT','FIRST_PLOT_POINT','FIRST_PINCH_POINT','MIDPOINT','SECOND_PINCH_POINT','SECOND_PLOT_POINT','CLIMAX','RESOLUTION','CHARACTER_ARC','RELATIONSHIP_DEVELOPMENT','WORLD_EXPANSION','THEME_REVELATION') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `planned_chapter` int(0) NULL DEFAULT NULL,
  `importance` enum('CRITICAL','HIGH','MEDIUM','LOW') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MEDIUM',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `requirements` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `impact` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `consequences` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `is_completed` tinyint(1) NULL DEFAULT 0,
  `completed_chapter` int(0) NULL DEFAULT NULL,
  `completion_date` timestamp(0) NULL DEFAULT NULL,
  `notes` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `novel_id` bigint(0) NOT NULL,
  `created_by` bigint(0) NULL DEFAULT NULL,
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `created_by`(`created_by`) USING BTREE,
  INDEX `idx_novel_id`(`novel_id`) USING BTREE,
  INDEX `idx_type`(`type`) USING BTREE,
  INDEX `idx_importance`(`importance`) USING BTREE,
  INDEX `idx_is_completed`(`is_completed`) USING BTREE,
  INDEX `idx_planned_chapter`(`planned_chapter`) USING BTREE,
  CONSTRAINT `plot_points_ibfk_1` FOREIGN KEY (`novel_id`) REFERENCES `novels` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `plot_points_ibfk_2` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for progress
-- ----------------------------
DROP TABLE IF EXISTS `progress`;
CREATE TABLE `progress`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `novel_id` bigint(0) NOT NULL,
  `current_chapter` int(0) NULL DEFAULT 1,
  `total_chapters` int(0) NULL DEFAULT 0,
  `completion_rate` decimal(5, 2) NULL DEFAULT 0.00,
  `current_stage` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '开篇',
  `word_count` int(0) NULL DEFAULT 0,
  `target_word_count` int(0) NULL DEFAULT 0,
  `estimated_completion` timestamp(0) NULL DEFAULT NULL,
  `daily_word_goal` int(0) NULL DEFAULT 1000,
  `last_writing_date` timestamp(0) NULL DEFAULT NULL,
  `writing_streak` int(0) NULL DEFAULT 0,
  `total_writing_time` bigint(0) NULL DEFAULT 0,
  `average_words_per_hour` decimal(8, 2) NULL DEFAULT 0.00,
  `notes` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `novel_id`(`novel_id`) USING BTREE,
  INDEX `idx_novel_id`(`novel_id`) USING BTREE,
  INDEX `idx_current_stage`(`current_stage`) USING BTREE,
  INDEX `idx_completion_rate`(`completion_rate`) USING BTREE,
  CONSTRAINT `progress_ibfk_1` FOREIGN KEY (`novel_id`) REFERENCES `novels` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for prompts
-- ----------------------------
DROP TABLE IF EXISTS `prompts`;
CREATE TABLE `prompts`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `category` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `style` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `difficulty` int(0) NULL DEFAULT 0,
  `tags` json NULL,
  `usage_count` int(0) NULL DEFAULT 0,
  `effectiveness_score` decimal(3, 2) NULL DEFAULT 0.00,
  `examples` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `author` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `is_public` tinyint(1) NULL DEFAULT 1,
  `created_by` bigint(0) NULL DEFAULT NULL,
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_category`(`category`) USING BTREE,
  INDEX `idx_style`(`style`) USING BTREE,
  INDEX `idx_created_by`(`created_by`) USING BTREE,
  INDEX `idx_is_public`(`is_public`) USING BTREE,
  CONSTRAINT `prompts_ibfk_1` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for roles
-- ----------------------------
DROP TABLE IF EXISTS `roles`;
CREATE TABLE `roles`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `permissions` json NULL,
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `name`(`name`) USING BTREE,
  INDEX `idx_name`(`name`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for scene_plans
-- ----------------------------
DROP TABLE IF EXISTS `scene_plans`;
CREATE TABLE `scene_plans`  (
  `id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `location` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `purpose` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `characters` json NULL COMMENT '参与角色列表',
  `key_actions` json NULL COMMENT '关键动作列表',
  `mood` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `estimated_duration` int(0) NULL DEFAULT 30 COMMENT '预计时长(分钟)',
  `chapter_plan_id` bigint(0) NULL DEFAULT NULL,
  `order_in_chapter` int(0) NULL DEFAULT 1,
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_chapter_plan_id`(`chapter_plan_id`) USING BTREE,
  INDEX `idx_order_in_chapter`(`order_in_chapter`) USING BTREE,
  INDEX `idx_location`(`location`) USING BTREE,
  CONSTRAINT `scene_plans_ibfk_1` FOREIGN KEY (`chapter_plan_id`) REFERENCES `chapter_plans` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '场景规划表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for super_outlines
-- ----------------------------
DROP TABLE IF EXISTS `super_outlines`;
CREATE TABLE `super_outlines`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `novel_id` bigint(0) NOT NULL COMMENT '关联小说ID',
  `original_idea` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户输入的原始构思',
  `title` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '超级大纲标题',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '超级大纲内容（JSON格式）',
  `core_theme` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '核心主题',
  `background` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '故事背景',
  `main_characters` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '主要角色',
  `plot_structure` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '情节结构',
  `world_building` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '世界观设定',
  `target_chapters` int(0) NULL DEFAULT NULL COMMENT '预期目标章数',
  `target_words` int(0) NULL DEFAULT NULL COMMENT '预期总字数',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DRAFT' COMMENT '超级大纲状态：DRAFT(草稿), OPTIMIZING(优化中), CONFIRMED(已确认)',
  `optimization_count` int(0) NULL DEFAULT 0 COMMENT 'AI优化次数',
  `user_rating` int(0) NULL DEFAULT NULL COMMENT '用户评分（1-5星）',
  `user_feedback` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '用户反馈意见',
  `last_optimization_suggestion` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '最后一次优化建议',
  `volumes_generated` tinyint(1) NULL DEFAULT 0 COMMENT '是否已生成卷规划',
  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `last_optimized_at` datetime(0) NULL DEFAULT NULL COMMENT '最后AI优化时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_novel_id`(`novel_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_created_at`(`created_at`) USING BTREE,
  CONSTRAINT `super_outlines_ibfk_1` FOREIGN KEY (`novel_id`) REFERENCES `novels` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '超级大纲表' ROW_FORMAT = Dynamic;

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
-- Table structure for user_sessions
-- ----------------------------
DROP TABLE IF EXISTS `user_sessions`;
CREATE TABLE `user_sessions`  (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint(0) NOT NULL,
  `ip_address` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `user_agent` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `login_time` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `last_activity` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  `expires_at` timestamp(0) NOT NULL,
  `is_active` tinyint(1) NULL DEFAULT 1,
  `device_info` json NULL,
  `location_info` json NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_expires_at`(`expires_at`) USING BTREE,
  INDEX `idx_is_active`(`is_active`) USING BTREE,
  INDEX `idx_last_activity`(`last_activity`) USING BTREE,
  CONSTRAINT `user_sessions_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户会话管理表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user_writing_settings
-- ----------------------------
DROP TABLE IF EXISTS `user_writing_settings`;
CREATE TABLE `user_writing_settings`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(0) NOT NULL,
  `daily_word_goal` int(0) NULL DEFAULT 1000,
  `preferred_writing_time` time(0) NULL DEFAULT '09:00:00',
  `default_genre` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `ai_assistance_level` enum('none','low','medium','high') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'medium',
  `auto_save_interval` int(0) NULL DEFAULT 300 COMMENT '自动保存间隔(秒)',
  `theme_preference` enum('light','dark','auto') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'light',
  `notification_settings` json NULL COMMENT '通知设置',
  `writing_reminders` json NULL COMMENT '写作提醒设置',
  `quality_threshold` decimal(3, 1) NULL DEFAULT 7.0,
  `backup_settings` json NULL COMMENT '备份设置',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `user_id`(`user_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  CONSTRAINT `user_writing_settings_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户写作设置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `nickname` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `avatar_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `bio` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `status` enum('ACTIVE','INACTIVE','BANNED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `last_login_at` timestamp(0) NULL DEFAULT NULL,
  `email_verified` tinyint(1) NULL DEFAULT 0,
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `username`(`username`) USING BTREE,
  UNIQUE INDEX `email`(`email`) USING BTREE,
  INDEX `idx_username`(`username`) USING BTREE,
  INDEX `idx_email`(`email`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for work_positions
-- ----------------------------
DROP TABLE IF EXISTS `work_positions`;
CREATE TABLE `work_positions`  (
  `id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `novel_id` bigint(0) NOT NULL,
  `target_audience` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `writing_style` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `narrative_perspective` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `tone` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `themes` json NULL COMMENT '主题列表',
  `unique_selling_points` json NULL COMMENT '独特卖点列表',
  `comparable_works` json NULL COMMENT '可比作品列表',
  `market_positioning` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `status` enum('DRAFT','ACTIVE','ARCHIVED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT',
  `is_ai_generated` tinyint(1) NULL DEFAULT 0,
  `created_by` bigint(0) NULL DEFAULT NULL,
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_novel_id`(`novel_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_created_by`(`created_by`) USING BTREE,
  CONSTRAINT `work_positions_ibfk_1` FOREIGN KEY (`novel_id`) REFERENCES `novels` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `work_positions_ibfk_2` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '作品定位表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for world_views
-- ----------------------------
DROP TABLE IF EXISTS `world_views`;
CREATE TABLE `world_views`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `background` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `rules` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `timeline` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `locations` json NULL,
  `magic_system` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `technology` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `social_structure` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `novel_id` bigint(0) NOT NULL,
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `novel_id`(`novel_id`) USING BTREE,
  INDEX `idx_novel_id`(`novel_id`) USING BTREE,
  INDEX `idx_name`(`name`) USING BTREE,
  CONSTRAINT `world_views_ibfk_1` FOREIGN KEY (`novel_id`) REFERENCES `novels` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for writing_suggestions
-- ----------------------------
DROP TABLE IF EXISTS `writing_suggestions`;
CREATE TABLE `writing_suggestions`  (
  `id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `novel_id` bigint(0) NOT NULL,
  `chapter_id` bigint(0) NULL DEFAULT NULL,
  `suggestions` json NOT NULL COMMENT '建议列表',
  `priority` enum('low','medium','high') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'medium',
  `category` enum('plot','character','style','pacing') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `reasoning` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `applied` tinyint(1) NULL DEFAULT 0,
  `applied_at` timestamp(0) NULL DEFAULT NULL,
  `effectiveness_rating` int(0) NULL DEFAULT NULL COMMENT '用户评分1-5',
  `created_by_agent` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_novel_id`(`novel_id`) USING BTREE,
  INDEX `idx_chapter_id`(`chapter_id`) USING BTREE,
  INDEX `idx_priority`(`priority`) USING BTREE,
  INDEX `idx_category`(`category`) USING BTREE,
  INDEX `idx_applied`(`applied`) USING BTREE,
  INDEX `idx_created_at`(`created_at`) USING BTREE,
  CONSTRAINT `writing_suggestions_ibfk_1` FOREIGN KEY (`novel_id`) REFERENCES `novels` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `writing_suggestions_ibfk_2` FOREIGN KEY (`chapter_id`) REFERENCES `chapters` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '写作建议表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for writing_techniques
-- ----------------------------
DROP TABLE IF EXISTS `writing_techniques`;
CREATE TABLE `writing_techniques`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `category` enum('NARRATION','DIALOGUE','DESCRIPTION','EMOTION','ACTION','ATMOSPHERE','CHARACTER','PLOT','PACING','SUSPENSE','HUMOR','METAPHOR') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `examples` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `effectiveness_score` decimal(3, 2) NULL DEFAULT 0.00,
  `usage_count` int(0) NULL DEFAULT 0,
  `difficulty_level` int(0) NULL DEFAULT 1,
  `tips` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `common_mistakes` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `related_techniques` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `is_public` tinyint(1) NULL DEFAULT 1,
  `created_by` bigint(0) NULL DEFAULT NULL,
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_name`(`name`) USING BTREE,
  INDEX `idx_category`(`category`) USING BTREE,
  INDEX `idx_difficulty_level`(`difficulty_level`) USING BTREE,
  INDEX `idx_is_public`(`is_public`) USING BTREE,
  INDEX `idx_created_by`(`created_by`) USING BTREE,
  CONSTRAINT `writing_techniques_ibfk_1` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
