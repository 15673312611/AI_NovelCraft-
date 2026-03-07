-- 增加短篇工作流扩展字段：故事设定、当前步骤、章节分析等

ALTER TABLE short_novels
  ADD COLUMN story_setting TEXT AFTER idea,
  ADD COLUMN active_step VARCHAR(100) NULL AFTER status;

ALTER TABLE short_novel_chapters
  ADD COLUMN last_adjustment TEXT AFTER brief,
  ADD COLUMN analysis_result TEXT AFTER review_result;
