SET NAMES utf8mb4;
USE mall_db;
INSERT IGNORE INTO t_goods (goods_num, goods_name, shop_num, category_id, price, stock, sales, goods_state, pic_url, detail) VALUES
('G193568', '纯棉圆领短袖T恤', 'SH417826', 3, 59.00, 150, 25, 1, '', '100%新疆棉，透气亲肤'),
('G675214', '宽松休闲牛仔裤', 'SH417826', 3, 129.00, 80, 18, 1, '', '弹力面料，百搭版型'),
('G483906', '加绒连帽卫衣', 'SH692051', 3, 159.00, 120, 40, 1, '', '加绒保暖，宽松休闲'),
('G256731', '轻薄短款羽绒服', 'SH692051', 3, 299.00, 60, 9, 1, '', '90%白鸭绒，轻便保暖');
