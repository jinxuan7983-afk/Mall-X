SET NAMES utf8mb4;
USE mall_db;
INSERT IGNORE INTO t_goods (goods_num, goods_name, shop_num, category_id, price, stock, sales, goods_state, pic_url, detail) VALUES
('G1000000005', '纯棉圆领短袖T恤', 'SH1000000001', 3, 59.00, 150, 25, 1, '', '100%新疆棉，透气亲肤'),
('G1000000006', '宽松休闲牛仔裤', 'SH1000000001', 3, 129.00, 80, 18, 1, '', '弹力面料，百搭版型'),
('G1000000007', '加绒连帽卫衣', 'SH1000000002', 3, 159.00, 120, 40, 1, '', '加绒保暖，宽松休闲'),
('G1000000008', '轻薄短款羽绒服', 'SH1000000002', 3, 299.00, 60, 9, 1, '', '90%白鸭绒，轻便保暖');
