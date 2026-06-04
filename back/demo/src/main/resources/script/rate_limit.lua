-- 滑动窗口限流 Lua 脚本
-- KEYS[1] = 限流 key
-- ARGV[1] = 窗口大小（秒）
-- ARGV[2] = 最大请求数
-- ARGV[3] = 当前时间戳（毫秒）
-- 返回值：1=允许，0=拒绝，当前计数

local key = KEYS[1]
local window = tonumber(ARGV[1])
local maxCount = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

-- 滑动窗口左边界
local windowLeft = now - window * 1000

-- 删除窗口外的旧记录
redis.call('ZREMRANGEBYSCORE', key, '-inf', windowLeft)

-- 当前窗口内请求数
local currentCount = redis.call('ZCARD', key)

if currentCount < maxCount then
    -- 未超限，添加当前请求记录
    redis.call('ZADD', key, now, now .. '-' .. math.random(1000000))
    redis.call('EXPIRE', key, window + 1)
    return {1, currentCount + 1}
else
    -- 超限，拒绝
    return {0, currentCount}
end
