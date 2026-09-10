-- 商品库存原子扣减
-- KEYS[1] = stock:goods:{goodsNum}
-- ARGV[1] = 扣减数量, ARGV[2] = 数据库当前库存(用于初始化Redis Key)
-- 返回 扣减后剩余库存; -1 表示库存不足
local stock = tonumber(redis.call('GET', KEYS[1]))
if stock == nil then
    redis.call('SET', KEYS[1], tonumber(ARGV[2]))
    stock = tonumber(ARGV[2])
end
local need = tonumber(ARGV[1])
if stock < need then
    return -1
end
redis.call('DECRBY', KEYS[1], need)
return stock - need
