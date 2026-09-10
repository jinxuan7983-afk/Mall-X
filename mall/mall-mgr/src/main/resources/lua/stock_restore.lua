-- 商品库存原子回补（订单超时关闭/失败回滚）
-- KEYS[1] = stock:goods:{goodsNum}, ARGV[1] = 回补数量
local stock = tonumber(redis.call('GET', KEYS[1]))
if stock == nil then
    redis.call('SET', KEYS[1], 0)
end
redis.call('INCRBY', KEYS[1], tonumber(ARGV[1]))
return 1
