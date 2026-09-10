SET NAMES utf8mb4;

-- =============================================================
-- Mall-X 多商家电商平台 · 初始化数据脚本 mallInsert.sql
-- 说明：密码存储规则 = MD5(明文 + "mallx-salt")，全部演示账号密码为 123456
-- =============================================================
USE mall_db;

-- 平台管理员：admin / 123456
INSERT INTO t_admin_user (username, password, real_name, role) VALUES
('admin', 'b351f15efe3274edd525912e29784f4d', '平台超级管理员', 'ADMIN');

-- 支付渠道基础配置（ALIPAY 沙箱参数留空时自动走模拟支付通道）
INSERT INTO t_channel_config (way_code, channel_name, switch_status, base_rate, gateway_url, notify_key) VALUES
('WECHAT',  '微信支付', 1, 0.0060, 'https://api.mch.weixin.qq.com',  'mallx-channel-key'),
('ALIPAY',  '支付宝',   1, 0.0060, 'https://openapi-sandbox.dl.alipaydev.com/gateway.do', 'mallx-channel-key'),
('UNIONPAY','银联支付', 1, 0.0050, 'https://gateway.95516.com',      'mallx-channel-key');

-- 演示商户：shop1 / 123456（已审核通过）
INSERT INTO t_mch (shop_num, shop_name, shop_phone, shop_address, username, password, shop_state) VALUES
('SH1000000001', '星选数码旗舰店', '13800000001', '广东省广州市天河区科技园1栋', 'shop1', 'b351f15efe3274edd525912e29784f4d', 1),
('SH1000000002', '鲜果之家',       '13800000002', '广东省深圳市南山区果园路8号', 'shop2', 'b351f15efe3274edd525912e29784f4d', 1);

-- 演示用户：user1 / 123456
INSERT INTO t_user (user_num, username, password, nickname, phone) VALUES
('U1000000001', 'user1', 'b351f15efe3274edd525912e29784f4d', '小明', '13900000001');

-- 商品类目
INSERT INTO t_category (category_name, parent_id, sort) VALUES
('数码', 0, 1), ('生鲜', 0, 2), ('服饰', 0, 3), ('图书', 0, 4);

-- 演示商品（库存将同步写入 Redis：stock:goods:{goodsNum}）
INSERT INTO t_goods (goods_num, goods_name, shop_num, category_id, price, stock, sales, goods_state, pic_url, detail) VALUES
('G1000000001', '无线蓝牙耳机 Pro',   'SH1000000001', 1, 199.00, 100, 12, 1, '', '高保真音质，40小时续航'),
('G1000000002', '智能机械键盘 87键',  'SH1000000001', 1, 299.00, 50,  8, 1, '', '全键无冲，青轴'),
('G1000000003', '赣南脐橙 5斤装',     'SH1000000002', 2, 39.90,  200, 30, 1, '', '产地直发，新鲜直达'),
('G1000000004', '智利车厘子 2斤装',   'SH1000000002', 2, 89.90,  80,  15, 1, '', '空运直达，个大味甜'),
('G1000000005', '纯棉圆领短袖T恤',    'SH1000000001', 3, 59.00,  150, 25, 1, '', '100%新疆棉，透气亲肤'),
('G1000000006', '宽松休闲牛仔裤',     'SH1000000001', 3, 129.00, 80,  18, 1, '', '弹力面料，百搭版型'),
('G1000000007', '加绒连帽卫衣',       'SH1000000002', 3, 159.00, 120, 40, 1, '', '加绒保暖，宽松休闲'),
('G1000000008', '轻薄短款羽绒服',     'SH1000000002', 3, 299.00,  60,  9, 1, '', '90%白鸭绒，轻便保暖'),
('G1000000009', '三体（全三册）',     'SH1000000001', 4, 99.00,  100, 66, 1, '', '雨果奖获奖科幻巨著'),
('G1000000010', '活着（余华代表作）', 'SH1000000002', 4, 45.00,   80, 42, 1, '', '经典长篇小说');
