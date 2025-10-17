-- Fix chapters.order_num insert errors by setting a safe default and backfilling
-- Context: MyBatis insert does not include order_num; column is NOT NULL without default
-- Result: java.sql.SQLException: Field 'order_num' doesn't have a default value

-- 1) Set a default value for order_num to avoid insert failures
ALTER TABLE chapters 
  MODIFY COLUMN order_num INT NOT NULL DEFAULT 0 COMMENT '排序号';

-- 2) Backfill existing rows where order_num is NULL or 0 using chapter_number when available
UPDATE chapters 
SET order_num = CASE 
  WHEN chapter_number IS NOT NULL THEN chapter_number 
  ELSE 0 
END
WHERE (order_num IS NULL OR order_num = 0);

