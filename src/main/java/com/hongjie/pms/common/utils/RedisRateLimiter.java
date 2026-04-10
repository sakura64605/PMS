package com.hongjie.pms.common.utils;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis令牌桶限流器
 */
@Slf4j
@Component
public class RedisRateLimiter {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private LuaScriptLoader luaScriptLoader;
    private RedisScript<Long> tokenBucketScript;


    
    @PostConstruct
    public void init() {

        tokenBucketScript = luaScriptLoader.getAdvancedTokenBucketScript();

    }
    
    /**
     * 尝试获取令牌
     * 
     * @param key 限流key
     * @param capacity 桶容量
     * @param refillRate 每秒填充速率
     * @return true-获取成功，false-被限流
     */
    public boolean tryAcquire(String key, int capacity, int refillRate, int duration, TimeUnit timeUnit) {
        return tryAcquire(key, capacity, refillRate, duration, timeUnit, 1);
    }

    public boolean tryAcquire(String key, int capacity, int refillRate, int duration, TimeUnit timeUnit, int requested) {
        try {
            long now = System.currentTimeMillis() / 1000;

            // 转换时间单位
            int timeUnitCode;
            switch (timeUnit) {
                case SECONDS:
                    timeUnitCode = 1;
                    break;
                case MINUTES:
                    timeUnitCode = 2;
                    break;
                case HOURS:
                    timeUnitCode = 3;
                    break;
                case DAYS:
                    timeUnitCode = 4;
                    break;
                default:
                    timeUnitCode = 1;
            }

            List<String> keys = Arrays.asList(key);

            Long result = redisTemplate.execute(
                    tokenBucketScript,
                    keys,
                    capacity,      // ARGV[1]: 桶容量
                    refillRate,      // ARGV[2]: count = capacity
                    duration,      // ARGV[3]: 时间窗口长度
                    timeUnitCode,  // ARGV[4]: 时间单位
                    now,           // ARGV[5]: 当前时间
                    requested      // ARGV[6]: 请求令牌数
            );

            return result != null && result == 1L;

        } catch (Exception e) {
            log.error("Redis限流异常", e);
            return true;
        }
    }
    
    /**
     * 获取当前令牌数（用于监控）
     */
    public Long getCurrentTokens(String key) {
        Object tokens = redisTemplate.opsForHash().get(key, "tokens");
        if (tokens == null) {
            return null;
        }
        return Long.valueOf(tokens.toString());
    }
    
    /**
     * 获取桶容量（用于监控）
     */
    public Integer getCapacity(String key) {
        // 可以从Redis获取，或者从配置读取
        return null;
    }
    
    /**
     * 删除限流key（用于测试）
     */
    public void deleteKey(String key) {
        redisTemplate.delete(key);
    }
}