-- 给文件夹表添加系统标记字段
ALTER TABLE novel_folder 
ADD COLUMN is_system BOOLEAN DEFAULT FALSE COMMENT '是否为系统默认文件夹（不可删除、不可重命名）';

