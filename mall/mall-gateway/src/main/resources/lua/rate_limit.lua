-- Redis+Lua 固定窗口原子限流脚本
-- KEYS[1] 限流Key, ARGV[1] 时间窗口内最大请求数, ARGV[2] 窗口大小(毫秒)
-- 返回 1=放行, 0=超限
local current = redis.call('INCR', KEYS[1])
if current == 1 then
    redis.call('PEXPIRE', KEYS[1], ARGV[2])
end
if current > tonumber(ARGV[1]) then
    return 0
end
return 1
