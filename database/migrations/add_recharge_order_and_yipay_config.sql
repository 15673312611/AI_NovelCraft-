-- 充值订单表 + 易支付配置
CREATE TABLE IF NOT EXISTS `credit_recharge_orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL COMMENT '业务订单号',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `package_id` bigint NOT NULL COMMENT '套餐ID',
  `package_name` varchar(100) NOT NULL COMMENT '套餐名称快照',
  `package_price` decimal(10,2) NOT NULL COMMENT '支付金额',
  `package_credits` bigint NOT NULL COMMENT '到账字数点',
  `payment_provider` varchar(32) NOT NULL DEFAULT 'YIPAY' COMMENT '支付渠道',
  `payment_type` varchar(16) NOT NULL COMMENT '支付类型: alipay/wxpay/qqpay/cashier',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/PAID/CLOSED/FAILED',
  `third_party_order_no` varchar(128) DEFAULT NULL COMMENT '第三方交易号',
  `payment_url` varchar(2000) DEFAULT NULL COMMENT '支付跳转链接',
  `client_ip` varchar(64) DEFAULT NULL COMMENT '客户端IP',
  `notify_raw` text COMMENT '回调原始参数',
  `paid_at` datetime DEFAULT NULL COMMENT '支付完成时间',
  `expired_at` datetime DEFAULT NULL COMMENT '过期时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recharge_order_no` (`order_no`),
  KEY `idx_recharge_user_status` (`user_id`, `status`),
  KEY `idx_recharge_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='充值订单表';

INSERT INTO `system_ai_config` (`config_key`, `config_value`, `description`, `is_encrypted`, `created_at`, `updated_at`)
VALUES
('payment_yipay_enabled', 'false', '是否启用易支付充值', 0, NOW(), NOW()),
('payment_yipay_gateway_url', '', '易支付网关地址(通常为 submit.php 地址)', 0, NOW(), NOW()),
('payment_yipay_pid', '', '易支付商户PID', 0, NOW(), NOW()),
('payment_yipay_key', '', '易支付商户密钥', 1, NOW(), NOW()),
('payment_yipay_notify_url', '', '易支付异步回调地址, 留空则自动拼接', 0, NOW(), NOW()),
('payment_yipay_return_url', '', '易支付同步跳转地址, 留空则默认返回前端设置页', 0, NOW(), NOW()),
('payment_yipay_supported_types', 'alipay,wxpay', '易支付可用支付方式', 0, NOW(), NOW()),
('payment_order_expire_minutes', '30', '充值订单过期时间(分钟)', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE
`description` = VALUES(`description`),
`updated_at` = NOW();
