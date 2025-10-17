-- 清理未被代码使用的数据库对象（请在测试库先执行验证）
-- 目标库：ai_novel

SET FOREIGN_KEY_CHECKS = 0;

-- 未用业务表
DROP TABLE IF EXISTS ai_agents;
DROP TABLE IF EXISTS ai_collaboration_tasks;
DROP TABLE IF EXISTS ai_generation_tasks;
DROP TABLE IF EXISTS ai_review_sessions;
DROP TABLE IF EXISTS knowledge_base;
DROP TABLE IF EXISTS login_history;
DROP TABLE IF EXISTS project_analytics;
DROP TABLE IF EXISTS project_deadlines;
DROP TABLE IF EXISTS project_milestones;
DROP TABLE IF EXISTS writing_sessions;
DROP TABLE IF EXISTS writing_streaks;
DROP TABLE IF EXISTS novel_segments;
DROP TABLE IF EXISTS reference_novels;
DROP TABLE IF EXISTS writing_templates;
-- 你刚查询为空且当前代码未使用的表
DROP TABLE IF EXISTS chapter_plans;
DROP TABLE IF EXISTS plot_points;
DROP TABLE IF EXISTS scene_plans;
DROP TABLE IF EXISTS writing_suggestions;
DROP TABLE IF EXISTS user_sessions;
DROP TABLE IF EXISTS novel_memory_versions;

-- 注意：仅清理旧方案表，保留新版 novel_foreshadowing
DROP TABLE IF EXISTS foreshadowing_plans;
DROP TABLE IF EXISTS foreshadowing;

-- 视图（可选）
DROP VIEW IF EXISTS chapter_progress;
DROP VIEW IF EXISTS novel_summary;

-- 触发器（可选）
DROP TRIGGER IF EXISTS after_chapter_insert;
DROP TRIGGER IF EXISTS after_chapter_update;
DROP TRIGGER IF EXISTS after_chapter_delete;

-- 存储过程（可选）
DROP PROCEDURE IF EXISTS AddColumnIfNotExists;
DROP PROCEDURE IF EXISTS GetNovelStatistics;
DROP PROCEDURE IF EXISTS UpdateNovelProgress;

-- 依赖触发器/视图的统计表（仅当你不再使用时再删除）
-- DROP TABLE IF EXISTS progress;

SET FOREIGN_KEY_CHECKS = 1;

