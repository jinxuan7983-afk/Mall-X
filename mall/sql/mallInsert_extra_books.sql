SET NAMES utf8mb4;
USE mall_db;
INSERT IGNORE INTO t_goods (goods_num, goods_name, shop_num, category_id, price, stock, sales, goods_state, pic_url, detail)
SELECT 'G1000000009', '三体（全三册）', 'SH1000000001', c.id, 99.00, 100, 66, 1, '', '雨果奖获奖科幻巨著'
FROM t_category c WHERE c.category_name = '图书';
INSERT IGNORE INTO t_goods (goods_num, goods_name, shop_num, category_id, price, stock, sales, goods_state, pic_url, detail)
SELECT 'G1000000010', '活着（余华代表作）', 'SH1000000002', c.id, 45.00, 80, 42, 1, '', '经典长篇小说'
FROM t_category c WHERE c.category_name = '图书';
