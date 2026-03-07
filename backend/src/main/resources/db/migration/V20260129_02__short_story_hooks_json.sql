-- 存储“看点标题+一句话核心”的完整JSON（用于工作流画布/回溯）

ALTER TABLE short_novels
  ADD COLUMN hooks_json TEXT AFTER outline;
