SET NAMES utf8mb4;
USE mall_db;
INSERT IGNORE INTO t_goods (goods_num, goods_name, shop_num, category_id, price, stock, sales, goods_state, pic_url, detail)
SELECT 'G914082', '三体（全三册）', 'SH417826', c.id, 99.00, 100, 66, 1, '', '雨果奖获奖科幻巨著'
FROM t_category c WHERE c.category_name = '图书';
INSERT IGNORE INTO t_goods (goods_num, goods_name, shop_num, category_id, price, stock, sales, goods_state, pic_url, detail)
SELECT 'G370546', '活着（余华代表作）', 'SH692051', c.id, 45.00, 80, 42, 1, '', '经典长篇小说'
FROM t_category c WHERE c.category_name = '图书';
