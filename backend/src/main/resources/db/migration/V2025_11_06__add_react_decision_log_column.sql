-- 添加 ReAct 决策循环详细日志字段
-- 说明：用于记录每次章节生成时的 ReAct 思考、工具调用与结果等详细轨迹
-- 类型：LONGTEXT（以容纳较长的结构化/JSON 文本）

ALTER TABLE `chapters`
  ADD COLUMN `react_decision_log` LONGTEXT NULL COMMENT 'ReAct决策循环详细日志（工具调用、思考、结果等）' AFTER `generation_context`;


