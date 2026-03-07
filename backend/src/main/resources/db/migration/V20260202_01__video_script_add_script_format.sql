-- 增加“剧本格式”字段：用于选择每集正文输出结构（集-场台本 / 分镜脚本）

ALTER TABLE video_scripts
  ADD COLUMN script_format VARCHAR(30) NOT NULL DEFAULT 'STORYBOARD' AFTER mode;
