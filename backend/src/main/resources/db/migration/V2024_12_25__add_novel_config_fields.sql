-- 为Novel表添加创作配置字段
ALTER TABLE novels 
ADD COLUMN target_total_chapters INT COMMENT '目标总章数' AFTER rating_count,
ADD COLUMN words_per_chapter INT COMMENT '每章字数' AFTER target_total_chapters,
ADD COLUMN planned_volume_count INT COMMENT '计划卷数' AFTER words_per_chapter,
ADD COLUMN total_word_target INT COMMENT '总字数目标' AFTER planned_volume_count;

-- 为现有数据设置默认值
UPDATE novels 
SET 
    target_total_chapters = 100,
    words_per_chapter = 3000, 
    planned_volume_count = 3,
    total_word_target = 300000
WHERE target_total_chapters IS NULL;