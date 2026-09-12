SET NAMES utf8mb4;

-- =============================================================
-- Mall-X 多商家电商平台 · 演示交易数据脚本 demoData.sql（可选）
-- 用途：在看板/统计/审核页面展示正常业务数据（近 7 天交易）。
-- 特性：
--   1) 时间基于执行时刻动态生成 —— 任意时间执行均为"近 7 天"，图表有走势；
--   2) 可重复执行 —— 脚本先按编号定位段(9000000000)清理上一次的演示数据；
--   3) 自洽 —— 订单/支付/退款/提现相互关联，手续费按渠道费率计算，商户余额充足；
--   4) 不追溯库存 —— 演示交易不改动商品库存与销量（种子值视为历史累计）。
-- 执行：docker exec -i mallx-mysql mysql -uroot -proot123 --default-character-set=utf8mb4 mall_db < sql/demoData.sql
-- =============================================================
USE mall_db;

-- 近 7 天日期（含当天）
SET @d6 = DATE_SUB(CURDATE(), INTERVAL 6 DAY);
SET @d5 = DATE_SUB(CURDATE(), INTERVAL 5 DAY);
SET @d4 = DATE_SUB(CURDATE(), INTERVAL 4 DAY);
SET @d3 = DATE_SUB(CURDATE(), INTERVAL 3 DAY);
SET @d2 = DATE_SUB(CURDATE(), INTERVAL 2 DAY);
SET @d1 = DATE_SUB(CURDATE(), INTERVAL 1 DAY);
SET @d0 = CURDATE();

-- ---------- 幂等清理（编号含定位段 9000000000，不会命中真实数据） ----------
DELETE FROM t_order_item   WHERE order_num LIKE 'SO%9000000000%';
DELETE FROM t_order        WHERE order_num LIKE 'SO%9000000000%';
DELETE FROM t_refund_order WHERE refund_order_id LIKE 'RF%9000000000%';
DELETE FROM t_pay_order    WHERE pay_order_id LIKE 'PAY%9000000000%';
DELETE FROM t_withdraw     WHERE withdraw_no LIKE 'WD%9000000000%';
DELETE FROM t_user         WHERE user_num = 'U1000000002';

-- ---------- 演示用户：user2 / 123456 ----------
INSERT INTO t_user (user_num, username, password, nickname, phone) VALUES
('U1000000002', 'user2', 'b351f15efe3274edd525912e29784f4d', '小美', '13800000002');

-- ---------- 订单（11 笔：已完成 6 / 已发货 1 / 待发货 2 / 待支付 1 / 已关闭 1） ----------
INSERT INTO t_order
(order_num, user_num, shop_num, total_money, order_state, pay_order_id,
 receiver_name, receiver_phone, receiver_address, logistics_company, logistics_no,
 pay_time, deliver_time, receive_time, close_time, created_at, updated_at) VALUES
-- 1. 6 天前 · 已完成（耳机×1）
(CONCAT('SO', DATE_FORMAT(@d6,'%Y%m%d'), '093000', '900000000001'), 'U1000000001', 'SH417826', 199.00, 3,
 CONCAT('PAY', DATE_FORMAT(@d6,'%Y%m%d'), '093100', '900000000001'),
 '小明', '13900000001', '广东省广州市天河区体育西路88号', '顺丰速运', 'SF1036582947',
 CONCAT(@d6,' 09:31:00'), CONCAT(@d6,' 15:00:00'), CONCAT(DATE_ADD(@d6, INTERVAL 2 DAY),' 10:00:00'), NULL,
 CONCAT(@d6,' 09:30:00'), CONCAT(@d6,' 09:30:00')),
-- 2. 6 天前 · 已完成（脐橙×2，全额退款成功）
(CONCAT('SO', DATE_FORMAT(@d6,'%Y%m%d'), '142000', '900000000002'), 'U1000000002', 'SH692051', 79.80, 3,
 CONCAT('PAY', DATE_FORMAT(@d6,'%Y%m%d'), '142100', '900000000002'),
 '小美', '13800000002', '广东省深圳市南山区海德三道168号', '中通快递', 'ZT8827364510',
 CONCAT(@d6,' 14:21:00'), CONCAT(@d6,' 18:00:00'), CONCAT(@d5,' 12:00:00'), NULL,
 CONCAT(@d6,' 14:20:00'), CONCAT(@d6,' 14:20:00')),
-- 3. 5 天前 · 已完成（键盘×1 + T恤×2）
(CONCAT('SO', DATE_FORMAT(@d5,'%Y%m%d'), '100500', '900000000003'), 'U1000000001', 'SH417826', 417.00, 3,
 CONCAT('PAY', DATE_FORMAT(@d5,'%Y%m%d'), '100600', '900000000003'),
 '小明', '13900000001', '广东省广州市天河区体育西路88号', '顺丰速运', 'SF1174926503',
 CONCAT(@d5,' 10:06:00'), CONCAT(@d5,' 16:00:00'), CONCAT(@d4,' 10:00:00'), NULL,
 CONCAT(@d5,' 10:05:00'), CONCAT(@d5,' 10:05:00')),
-- 4. 5 天前 · 已完成（车厘子×1）
(CONCAT('SO', DATE_FORMAT(@d5,'%Y%m%d'), '164000', '900000000004'), 'U1000000002', 'SH692051', 89.90, 3,
 CONCAT('PAY', DATE_FORMAT(@d5,'%Y%m%d'), '164100', '900000000004'),
 '小美', '13800000002', '广东省深圳市南山区海德三道168号', '京东物流', 'JD5501827364',
 CONCAT(@d5,' 16:41:00'), CONCAT(@d4,' 09:00:00'), CONCAT(@d3,' 14:00:00'), NULL,
 CONCAT(@d5,' 16:40:00'), CONCAT(@d5,' 16:40:00')),
-- 5. 4 天前 · 已完成（三体×1）
(CONCAT('SO', DATE_FORMAT(@d4,'%Y%m%d'), '091200', '900000000005'), 'U1000000002', 'SH417826', 99.00, 3,
 CONCAT('PAY', DATE_FORMAT(@d4,'%Y%m%d'), '091300', '900000000005'),
 '小美', '13800000002', '广东省深圳市南山区海德三道168号', '圆通速递', 'YT7203948156',
 CONCAT(@d4,' 09:13:00'), CONCAT(@d4,' 14:00:00'), CONCAT(@d3,' 18:00:00'), NULL,
 CONCAT(@d4,' 09:12:00'), CONCAT(@d4,' 09:12:00')),
-- 6. 3 天前 · 已完成（卫衣×1 + 活着×2）
(CONCAT('SO', DATE_FORMAT(@d3,'%Y%m%d'), '153300', '900000000006'), 'U1000000001', 'SH692051', 249.00, 3,
 CONCAT('PAY', DATE_FORMAT(@d3,'%Y%m%d'), '153400', '900000000006'),
 '小明', '13900000001', '广东省广州市天河区体育西路88号', '韵达快递', 'YD3391027465',
 CONCAT(@d3,' 15:34:00'), CONCAT(@d3,' 18:30:00'), CONCAT(@d2,' 12:00:00'), NULL,
 CONCAT(@d3,' 15:33:00'), CONCAT(@d3,' 15:33:00')),
-- 7. 2 天前 · 已发货（牛仔裤×1，可演示用户端"确认收货"）
(CONCAT('SO', DATE_FORMAT(@d2,'%Y%m%d'), '112000', '900000000007'), 'U1000000001', 'SH417826', 129.00, 2,
 CONCAT('PAY', DATE_FORMAT(@d2,'%Y%m%d'), '112100', '900000000007'),
 '小明', '13900000001', '广东省广州市天河区体育西路88号', '顺丰速运', 'SF2093751648',
 CONCAT(@d2,' 11:21:00'), CONCAT(@d2,' 13:00:00'), NULL, NULL,
 CONCAT(@d2,' 11:20:00'), CONCAT(@d2,' 11:20:00')),
-- 8. 1 天前 · 待发货（羽绒服×1，另有部分退款审核中）
(CONCAT('SO', DATE_FORMAT(@d1,'%Y%m%d'), '104500', '900000000008'), 'U1000000002', 'SH692051', 299.00, 1,
 CONCAT('PAY', DATE_FORMAT(@d1,'%Y%m%d'), '104600', '900000000008'),
 '小美', '13800000002', '广东省深圳市南山区海德三道168号', NULL, NULL,
 CONCAT(@d1,' 10:46:00'), NULL, NULL, NULL,
 CONCAT(@d1,' 10:45:00'), CONCAT(@d1,' 10:45:00')),
-- 9. 1 天前 · 待发货（耳机×2）
(CONCAT('SO', DATE_FORMAT(@d1,'%Y%m%d'), '095000', '900000000009'), 'U1000000001', 'SH417826', 398.00, 1,
 CONCAT('PAY', DATE_FORMAT(@d1,'%Y%m%d'), '095100', '900000000009'),
 '小明', '13900000001', '广东省广州市天河区体育西路88号', NULL, NULL,
 CONCAT(@d1,' 09:51:00'), NULL, NULL, NULL,
 CONCAT(@d1,' 09:50:00'), CONCAT(@d1,' 09:50:00')),
-- 10. 今天 · 待支付（键盘×1，可演示"去支付/取消订单"）
(CONCAT('SO', DATE_FORMAT(@d0,'%Y%m%d'), '083000', '900000000010'), 'U1000000001', 'SH417826', 299.00, 0,
 CONCAT('PAY', DATE_FORMAT(@d0,'%Y%m%d'), '083100', '900000000010'),
 '小明', '13900000001', '广东省广州市天河区体育西路88号', NULL, NULL,
 NULL, NULL, NULL, NULL,
 CONCAT(@d0,' 08:30:00'), CONCAT(@d0,' 08:30:00')),
-- 11. 今天 · 已关闭（车厘子×3，超时未支付）
(CONCAT('SO', DATE_FORMAT(@d0,'%Y%m%d'), '071000', '900000000011'), 'U1000000001', 'SH692051', 269.70, 4,
 CONCAT('PAY', DATE_FORMAT(@d0,'%Y%m%d'), '071100', '900000000011'),
 '小明', '13900000001', '广东省广州市天河区体育西路88号', NULL, NULL,
 NULL, NULL, NULL, CONCAT(@d0,' 07:40:00'),
 CONCAT(@d0,' 07:10:00'), CONCAT(@d0,' 07:10:00'));

-- ---------- 订单明细 ----------
INSERT INTO t_order_item (order_num, goods_num, goods_name, goods_price, goods_count, total_money) VALUES
(CONCAT('SO', DATE_FORMAT(@d6,'%Y%m%d'), '093000', '900000000001'), 'G728415', '无线蓝牙耳机 Pro',   199.00, 1, 199.00),
(CONCAT('SO', DATE_FORMAT(@d6,'%Y%m%d'), '142000', '900000000002'), 'G562039', '赣南脐橙 5斤装',     39.90,  2, 79.80),
(CONCAT('SO', DATE_FORMAT(@d5,'%Y%m%d'), '100500', '900000000003'), 'G391647', '智能机械键盘 87键',  299.00, 1, 299.00),
(CONCAT('SO', DATE_FORMAT(@d5,'%Y%m%d'), '100500', '900000000003'), 'G193568', '纯棉圆领短袖T恤',    59.00,  2, 118.00),
(CONCAT('SO', DATE_FORMAT(@d5,'%Y%m%d'), '164000', '900000000004'), 'G847152', '智利车厘子 2斤装',   89.90,  1, 89.90),
(CONCAT('SO', DATE_FORMAT(@d4,'%Y%m%d'), '091200', '900000000005'), 'G914082', '三体（全三册）',     99.00,  1, 99.00),
(CONCAT('SO', DATE_FORMAT(@d3,'%Y%m%d'), '153300', '900000000006'), 'G483906', '加绒连帽卫衣',      159.00, 1, 159.00),
(CONCAT('SO', DATE_FORMAT(@d3,'%Y%m%d'), '153300', '900000000006'), 'G370546', '活着（余华代表作）', 45.00,  2, 90.00),
(CONCAT('SO', DATE_FORMAT(@d2,'%Y%m%d'), '112000', '900000000007'), 'G675214', '宽松休闲牛仔裤',    129.00, 1, 129.00),
(CONCAT('SO', DATE_FORMAT(@d1,'%Y%m%d'), '104500', '900000000008'), 'G256731', '轻薄短款羽绒服',    299.00, 1, 299.00),
(CONCAT('SO', DATE_FORMAT(@d1,'%Y%m%d'), '095000', '900000000009'), 'G728415', '无线蓝牙耳机 Pro',   199.00, 2, 398.00),
(CONCAT('SO', DATE_FORMAT(@d0,'%Y%m%d'), '083000', '900000000010'), 'G391647', '智能机械键盘 87键',  299.00, 1, 299.00),
(CONCAT('SO', DATE_FORMAT(@d0,'%Y%m%d'), '071000', '900000000011'), 'G847152', '智利车厘子 2斤装',   89.90,  3, 269.70);

-- ---------- 支付订单（与订单一一对应；手续费按渠道费率计算，shop2 的 ALIPAY 走差异化费率 0.0036） ----------
INSERT INTO t_pay_order
(pay_order_id, mch_order_no, shop_num, shop_name, app_id, way_code, money, mch_fee, subject, user_num,
 order_state, channel_order_no, pay_time, expire_time, created_at, updated_at) VALUES
(CONCAT('PAY', DATE_FORMAT(@d6,'%Y%m%d'), '093100', '900000000001'),
 CONCAT('SO', DATE_FORMAT(@d6,'%Y%m%d'), '093000', '900000000001'), 'SH417826', '星选数码旗舰店', 'mallx-app', 'WECHAT', 199.00,
 ROUND(199.00*0.0060,2), '无线蓝牙耳机 Pro x1', 'U1000000001', 0,
 CONCAT('CH', DATE_FORMAT(@d6,'%Y%m%d'), '093200', '900000000001'), CONCAT(@d6,' 09:31:00'), NULL, CONCAT(@d6,' 09:30:00'), CONCAT(@d6,' 09:31:00')),
(CONCAT('PAY', DATE_FORMAT(@d6,'%Y%m%d'), '142100', '900000000002'),
 CONCAT('SO', DATE_FORMAT(@d6,'%Y%m%d'), '142000', '900000000002'), 'SH692051', '鲜果之家', 'mallx-app', 'ALIPAY', 79.80,
 ROUND(79.80*0.0036,2), '赣南脐橙 5斤装 x2', 'U1000000002', 2,
 CONCAT('CH', DATE_FORMAT(@d6,'%Y%m%d'), '142200', '900000000002'), CONCAT(@d6,' 14:21:00'), NULL, CONCAT(@d6,' 14:20:00'), CONCAT(@d5,' 09:00:00')),
(CONCAT('PAY', DATE_FORMAT(@d5,'%Y%m%d'), '100600', '900000000003'),
 CONCAT('SO', DATE_FORMAT(@d5,'%Y%m%d'), '100500', '900000000003'), 'SH417826', '星选数码旗舰店', 'mallx-app', 'WECHAT', 417.00,
 ROUND(417.00*0.0060,2), '智能机械键盘 87键 x1, 纯棉圆领短袖T恤 x2', 'U1000000001', 0,
 CONCAT('CH', DATE_FORMAT(@d5,'%Y%m%d'), '100700', '900000000003'), CONCAT(@d5,' 10:06:00'), NULL, CONCAT(@d5,' 10:05:00'), CONCAT(@d5,' 10:06:00')),
(CONCAT('PAY', DATE_FORMAT(@d5,'%Y%m%d'), '164100', '900000000004'),
 CONCAT('SO', DATE_FORMAT(@d5,'%Y%m%d'), '164000', '900000000004'), 'SH692051', '鲜果之家', 'mallx-app', 'UNIONPAY', 89.90,
 ROUND(89.90*0.0050,2), '智利车厘子 2斤装 x1', 'U1000000002', 0,
 CONCAT('CH', DATE_FORMAT(@d5,'%Y%m%d'), '164200', '900000000004'), CONCAT(@d5,' 16:41:00'), NULL, CONCAT(@d5,' 16:40:00'), CONCAT(@d5,' 16:41:00')),
(CONCAT('PAY', DATE_FORMAT(@d4,'%Y%m%d'), '091300', '900000000005'),
 CONCAT('SO', DATE_FORMAT(@d4,'%Y%m%d'), '091200', '900000000005'), 'SH417826', '星选数码旗舰店', 'mallx-app', 'ALIPAY', 99.00,
 ROUND(99.00*0.0060,2), '三体（全三册） x1', 'U1000000002', 0,
 CONCAT('CH', DATE_FORMAT(@d4,'%Y%m%d'), '091400', '900000000005'), CONCAT(@d4,' 09:13:00'), NULL, CONCAT(@d4,' 09:12:00'), CONCAT(@d4,' 09:13:00')),
(CONCAT('PAY', DATE_FORMAT(@d3,'%Y%m%d'), '153400', '900000000006'),
 CONCAT('SO', DATE_FORMAT(@d3,'%Y%m%d'), '153300', '900000000006'), 'SH692051', '鲜果之家', 'mallx-app', 'WECHAT', 249.00,
 ROUND(249.00*0.0060,2), '加绒连帽卫衣 x1, 活着（余华代表作） x2', 'U1000000001', 0,
 CONCAT('CH', DATE_FORMAT(@d3,'%Y%m%d'), '153500', '900000000006'), CONCAT(@d3,' 15:34:00'), NULL, CONCAT(@d3,' 15:33:00'), CONCAT(@d3,' 15:34:00')),
(CONCAT('PAY', DATE_FORMAT(@d2,'%Y%m%d'), '112100', '900000000007'),
 CONCAT('SO', DATE_FORMAT(@d2,'%Y%m%d'), '112000', '900000000007'), 'SH417826', '星选数码旗舰店', 'mallx-app', 'UNIONPAY', 129.00,
 ROUND(129.00*0.0050,2), '宽松休闲牛仔裤 x1', 'U1000000001', 0,
 CONCAT('CH', DATE_FORMAT(@d2,'%Y%m%d'), '112200', '900000000007'), CONCAT(@d2,' 11:21:00'), NULL, CONCAT(@d2,' 11:20:00'), CONCAT(@d2,' 11:21:00')),
(CONCAT('PAY', DATE_FORMAT(@d1,'%Y%m%d'), '104600', '900000000008'),
 CONCAT('SO', DATE_FORMAT(@d1,'%Y%m%d'), '104500', '900000000008'), 'SH692051', '鲜果之家', 'mallx-app', 'ALIPAY', 299.00,
 ROUND(299.00*0.0036,2), '轻薄短款羽绒服 x1', 'U1000000002', 0,
 CONCAT('CH', DATE_FORMAT(@d1,'%Y%m%d'), '104700', '900000000008'), CONCAT(@d1,' 10:46:00'), NULL, CONCAT(@d1,' 10:45:00'), CONCAT(@d1,' 10:46:00')),
(CONCAT('PAY', DATE_FORMAT(@d1,'%Y%m%d'), '095100', '900000000009'),
 CONCAT('SO', DATE_FORMAT(@d1,'%Y%m%d'), '095000', '900000000009'), 'SH417826', '星选数码旗舰店', 'mallx-app', 'WECHAT', 398.00,
 ROUND(398.00*0.0060,2), '无线蓝牙耳机 Pro x2', 'U1000000001', 0,
 CONCAT('CH', DATE_FORMAT(@d1,'%Y%m%d'), '095200', '900000000009'), CONCAT(@d1,' 09:51:00'), NULL, CONCAT(@d1,' 09:50:00'), CONCAT(@d1,' 09:51:00')),
(CONCAT('PAY', DATE_FORMAT(@d0,'%Y%m%d'), '083100', '900000000010'),
 CONCAT('SO', DATE_FORMAT(@d0,'%Y%m%d'), '083000', '900000000010'), 'SH417826', '星选数码旗舰店', 'mallx-app', 'ALIPAY', 299.00,
 ROUND(299.00*0.0060,2), '智能机械键盘 87键 x1', 'U1000000001', 4,
 NULL, NULL, CONCAT(@d0,' 09:00:00'), CONCAT(@d0,' 08:30:00'), CONCAT(@d0,' 08:30:00')),
(CONCAT('PAY', DATE_FORMAT(@d0,'%Y%m%d'), '071100', '900000000011'),
 CONCAT('SO', DATE_FORMAT(@d0,'%Y%m%d'), '071000', '900000000011'), 'SH692051', '鲜果之家', 'mallx-app', 'WECHAT', 269.70,
 ROUND(269.70*0.0060,2), '智利车厘子 2斤装 x3', 'U1000000001', 3,
 NULL, NULL, CONCAT(@d0,' 07:40:00'), CONCAT(@d0,' 07:10:00'), CONCAT(@d0,' 07:40:00'));

-- ---------- 退款单（1 笔已完成 + 1 笔待审核，供用户端/商户端退款流程演示） ----------
INSERT INTO t_refund_order
(refund_order_id, pay_order_id, order_num, shop_num, user_num, refund_money, refund_reason, evidence, refund_state,
 channel_refund_no, audit_remark, audit_time, refund_time, created_at, updated_at) VALUES
(CONCAT('RF', DATE_FORMAT(@d5,'%Y%m%d'), '090000', '900000000001'),
 CONCAT('PAY', DATE_FORMAT(@d6,'%Y%m%d'), '142100', '900000000002'),
 CONCAT('SO', DATE_FORMAT(@d6,'%Y%m%d'), '142000', '900000000002'), 'SH692051', 'U1000000002', 79.80, '水果有磕碰，申请全额退款', NULL, 2,
 CONCAT('CH', DATE_FORMAT(@d5,'%Y%m%d'), '090100', '900000000001'), '情况属实，同意退款', CONCAT(@d5,' 09:00:00'), CONCAT(@d5,' 09:00:00'), CONCAT(@d5,' 08:30:00'), CONCAT(@d5,' 09:00:00')),
(CONCAT('RF', DATE_FORMAT(@d0,'%Y%m%d'), '082000', '900000000002'),
 CONCAT('PAY', DATE_FORMAT(@d1,'%Y%m%d'), '104600', '900000000008'),
 CONCAT('SO', DATE_FORMAT(@d1,'%Y%m%d'), '104500', '900000000008'), 'SH692051', 'U1000000002', 99.00, '尺码不合适，申请部分退款', NULL, 0,
 NULL, NULL, NULL, NULL, CONCAT(@d0,' 08:20:00'), CONCAT(@d0,' 08:20:00'));

-- ---------- 提现单（1 笔已到账 + 1 笔审核中，供管理端提现审核演示） ----------
INSERT INTO t_withdraw (withdraw_no, shop_num, money, state, remark, finish_time, created_at) VALUES
(CONCAT('WD', DATE_FORMAT(@d3,'%Y%m%d'), '150000', '900000000001'), 'SH417826', 200.00, 1, '审核通过，已打款', CONCAT(@d3,' 16:00:00'), CONCAT(@d3,' 15:00:00')),
(CONCAT('WD', DATE_FORMAT(@d0,'%Y%m%d'), '081000', '900000000002'), 'SH692051', 60.00, 0, NULL, NULL, CONCAT(@d0,' 08:10:00'));

SELECT '演示数据写入完成' AS result,
       (SELECT COUNT(*) FROM t_order WHERE order_num LIKE 'SO%9000000000%')        AS orders,
       (SELECT COUNT(*) FROM t_pay_order WHERE pay_order_id LIKE 'PAY%9000000000%') AS pay_orders,
       (SELECT COUNT(*) FROM t_refund_order WHERE refund_order_id LIKE 'RF%9000000000%') AS refunds,
       (SELECT COUNT(*) FROM t_withdraw WHERE withdraw_no LIKE 'WD%9000000000%')   AS withdraws;
