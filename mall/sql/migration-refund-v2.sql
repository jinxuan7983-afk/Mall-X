SET NAMES utf8mb4;

-- =============================================================
-- 退款售后功能升级迁移脚本（一次性执行）
-- 1) t_refund_order 扩展：关联业务订单号 / 申请用户 / 退款凭证 / 审核时间；
-- 2) 退款状态语义升级为四态：0-待审核 1-处理中 2-已完成 3-已拒绝
--    （旧值迁移：2-退款失败 → 3-已拒绝；1-退款成功 → 2-已完成）
-- 执行：docker exec -i mallx-mysql mysql -uroot -proot123 --default-character-set=utf8mb4 < sql/migration-refund-v2.sql
-- =============================================================
USE mall_db;

ALTER TABLE t_refund_order
    ADD COLUMN order_num VARCHAR(64) DEFAULT NULL COMMENT '关联业务订单号(用户端按订单申请时记录)' AFTER pay_order_id,
    ADD COLUMN user_num VARCHAR(32) DEFAULT NULL COMMENT '申请用户编号(用户端发起时记录)' AFTER shop_num,
    ADD COLUMN evidence VARCHAR(1024) DEFAULT NULL COMMENT '退款凭证图片地址(JSON数组)' AFTER refund_reason,
    ADD COLUMN audit_time DATETIME DEFAULT NULL COMMENT '审核时间' AFTER audit_remark,
    ADD KEY idx_refund_user (user_num);

UPDATE t_refund_order SET refund_state = 3 WHERE refund_state = 2;
UPDATE t_refund_order SET refund_state = 2 WHERE refund_state = 1;

ALTER TABLE t_refund_order
    MODIFY COLUMN refund_state TINYINT NOT NULL DEFAULT 0 COMMENT '退款状态 0-待审核 1-处理中 2-已完成 3-已拒绝';

SELECT '退款表升级完成' AS result,
       (SELECT COUNT(*) FROM t_refund_order) AS refunds,
       (SELECT COUNT(*) FROM t_refund_order WHERE refund_state = 0) AS wait_count,
       (SELECT COUNT(*) FROM t_refund_order WHERE refund_state = 2) AS done_count,
       (SELECT COUNT(*) FROM t_refund_order WHERE refund_state = 3) AS rejected_count;
