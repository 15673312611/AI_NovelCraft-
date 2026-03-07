-- 添加导语（黄金开头）字段

ALTER TABLE short_novels
  ADD COLUMN prologue TEXT AFTER hooks_json;
