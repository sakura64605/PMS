-- token_bucket_advanced.lua
-- 增强版令牌桶，支持秒/分/时/天
-- KEYS[1]: 限流key
-- ARGV[1]: 桶容量 (capacity)
-- ARGV[2]: 时间窗口内允许的次数 (count)
-- ARGV[3]: 时间窗口长度 (duration)
-- ARGV[4]: 时间单位 (1:秒, 2:分, 3:时, 4:天)
-- ARGV[5]: 当前时间戳（秒）
-- ARGV[6]: 请求的令牌数（默认1）
-- 返回值: 1-获取成功, 0-被限流

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local count = tonumber(ARGV[2])
local duration = tonumber(ARGV[3])
local timeUnit = tonumber(ARGV[4])
local now = tonumber(ARGV[5])
local requested = tonumber(ARGV[6]) or 1

-- 将时间窗口转换为秒
local windowSeconds = duration
if timeUnit == 2 then      -- 分钟
    windowSeconds = duration * 60
elseif timeUnit == 3 then  -- 小时
    windowSeconds = duration * 3600
elseif timeUnit == 4 then  -- 天
    windowSeconds = duration * 86400
end

-- 计算每秒填充速率
local refillRate = count / windowSeconds

-- 获取当前桶状态
local bucket = redis.call('hmget', key, 'tokens', 'lastRefillTime')
local tokens = tonumber(bucket[1])
local lastRefillTime = tonumber(bucket[2])

-- 初始化桶
if tokens == nil then
    tokens = capacity
    lastRefillTime = now
end

-- 计算需要补充的令牌数
local elapsedTime = math.max(0, now - lastRefillTime)
local refillTokens = math.floor(elapsedTime * refillRate)
tokens = math.min(capacity, tokens + refillTokens)

-- 判断是否有足够的令牌
if tokens >= requested then
    tokens = tokens - requested
    redis.call('hmset', key, 'tokens', tokens, 'lastRefillTime', now)
    local ttl = math.ceil(capacity / refillRate) + 10
    redis.call('expire', key, ttl)
    return 1
else
    -- 计算需要等待的时间（秒）
    local needTokens = requested - tokens
    local waitSeconds = math.ceil(needTokens / refillRate)
    return 0, waitSeconds
end