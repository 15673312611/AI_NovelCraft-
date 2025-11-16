-- 卷进度配置诊断脚本
-- 用于排查"500章÷5卷=20章"的bug

-- 1. 检查小说表的配置
SELECT 
    id,
    title,
    planned_volume_count AS '规划卷数',
    target_total_chapters AS '目标总章节数',
    CASE 
        WHEN target_total_chapters IS NULL OR target_total_chapters = 0 THEN '❌ 未设置'
        ELSE CONCAT('✅ ', target_total_chapters, '章')
    END AS '总章节状态'
FROM novels
WHERE id = 1; -- 替换为您的小说ID

-- 2. 检查卷表的配置
SELECT 
    volume_number AS '卷号',
    title AS '卷名',
    chapter_start AS '起始章',
    chapter_end AS '结束章',
    CASE 
        WHEN chapter_start IS NOT NULL AND chapter_end IS NOT NULL 
        THEN chapter_end - chapter_start + 1
        ELSE NULL
    END AS '实际跨度',
    CASE 
        WHEN chapter_start IS NULL OR chapter_end IS NULL THEN '⚪ 未设置（使用自动计算）'
        WHEN chapter_end - chapter_start + 1 = 100 THEN '✅ 正常 (100章)'
        WHEN chapter_end - chapter_start + 1 < 100 THEN CONCAT('⚠️ 偏小 (', chapter_end - chapter_start + 1, '章)')
        ELSE CONCAT('⚠️ 偏大 (', chapter_end - chapter_start + 1, '章)')
    END AS '状态'
FROM novel_volumes
WHERE novel_id = 1 -- 替换为您的小说ID
ORDER BY volume_number;

-- 3. 诊断分析
SELECT 
    '诊断结果' AS '类型',
    CASE 
        -- 场景1：小说表设置了500章
        WHEN (SELECT target_total_chapters FROM novels WHERE id = 1) = 500 THEN
            CASE 
                -- 检查是否有卷设置了错误的范围
                WHEN EXISTS (
                    SELECT 1 FROM novel_volumes 
                    WHERE novel_id = 1 
                    AND chapter_end IS NOT NULL 
                    AND chapter_end < 500
                ) THEN '⚠️ 小说表设置500章，但部分卷设置了较小的chapter_end，可能导致误判'
                ELSE '✅ 小说表正确设置500章，且卷配置正常'
            END
        
        -- 场景2：小说表未设置
        WHEN (SELECT target_total_chapters FROM novels WHERE id = 1) IS NULL THEN
            CASE 
                -- 检查卷数据
                WHEN (SELECT COUNT(*) FROM novel_volumes WHERE novel_id = 1 AND chapter_end IS NOT NULL) > 0 THEN
                    CONCAT('❌ BUG触发！小说表未设置总章节，系统从卷数据推断为 ', 
                        (SELECT MAX(chapter_end) FROM novel_volumes WHERE novel_id = 1),
                        '章，导致计算错误')
                ELSE '⚪ 小说表未设置，将使用默认计算（5卷×100章=500章）'
            END
        
        ELSE CONCAT('⚠️ 小说表设置为 ', 
            (SELECT target_total_chapters FROM novels WHERE id = 1), 
            ' 章，请确认是否正确')
    END AS '诊断信息',
    
    CASE 
        WHEN (SELECT target_total_chapters FROM novels WHERE id = 1) = 500 THEN
            '无需修复，配置正确'
        WHEN (SELECT target_total_chapters FROM novels WHERE id = 1) IS NULL THEN
            'UPDATE novels SET target_total_chapters = 500 WHERE id = 1;'
        ELSE 
            CONCAT('UPDATE novels SET target_total_chapters = 500 WHERE id = 1; -- 当前值: ', 
                (SELECT target_total_chapters FROM novels WHERE id = 1))
    END AS '修复SQL';

-- 4. 如果需要清空卷的手动设置，使用以下SQL（谨慎执行）
-- UPDATE novel_volumes SET chapter_start = NULL, chapter_end = NULL WHERE novel_id = 1;

-- 5. 预期的正确计算流程
SELECT 
    '预期计算' AS '步骤',
    '500总章节 ÷ 5卷 = 100章/卷' AS '结果';

-- 6. 查看当前会如何计算（模拟）
SELECT 
    '当前计算' AS '步骤',
    CONCAT(
        COALESCE((SELECT target_total_chapters FROM novels WHERE id = 1), 
            COALESCE((SELECT MAX(chapter_end) FROM novel_volumes WHERE novel_id = 1),
                (SELECT planned_volume_count FROM novels WHERE id = 1) * 100)),
        '总章节 ÷ ',
        COALESCE((SELECT planned_volume_count FROM novels WHERE id = 1), 5),
        '卷 = ',
        COALESCE((SELECT target_total_chapters FROM novels WHERE id = 1), 
            COALESCE((SELECT MAX(chapter_end) FROM novel_volumes WHERE novel_id = 1),
                (SELECT planned_volume_count FROM novels WHERE id = 1) * 100)) / 
        COALESCE((SELECT planned_volume_count FROM novels WHERE id = 1), 5),
        '章/卷'
    ) AS '计算结果';

