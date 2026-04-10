package com.hongjie.pms.common.utils;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 限流器管理器（支持秒/分/时/天）
 */
@Deprecated
@Slf4j
@Component
public class RateLimiterManager {

    /**
     * 使用 Guava Cache 存储限流计数
     * key: 限流标识
     * value: 计数器（包含剩余次数和重置时间）
     */
    private final LoadingCache<String, RateLimitCounter> counterCache;

    public RateLimiterManager() {
        counterCache = CacheBuilder.newBuilder()
                .maximumSize(10000)           // 最大缓存数量
                .expireAfterWrite(24, TimeUnit.HOURS)  // 24小时过期
                .build(new CacheLoader<String, RateLimitCounter>() {
                    @Override
                    public RateLimitCounter load(String key) {
                        return new RateLimitCounter();
                    }
                });
    }

    /**
     * 尝试获取许可
     *
     * @param key 限流key
     * @param count 时间窗口内允许的次数
     * @param duration 时间窗口长度
     * @param timeUnit 时间单位
     * @return true-获取成功，false-被限流
     */
    public boolean tryAcquire(String key, int count, int duration, TimeUnit timeUnit) {
        try {
            RateLimitCounter counter = counterCache.get(key);
            long currentTime = System.currentTimeMillis();
            long windowMillis = timeUnit.toMillis(duration);

            synchronized (counter) {
                // 检查是否需要重置窗口
                if (currentTime - counter.getWindowStartTime() > windowMillis) {
                    // 重置窗口
                    counter.setCount(0);
                    counter.setWindowStartTime(currentTime);
                }

                // 检查是否超过限制
                if (counter.getCount() < count) {
                    counter.increment();
                    return true;
                }

                return false;
            }
        } catch (Exception e) {
            log.error("限流检查异常: {}", e.getMessage(), e);
            return true; // 异常时放行
        }
    }

    /**
     * 获取剩余可用次数
     */
    public int getRemaining(String key, int count, int duration, TimeUnit timeUnit) {
        try {
            RateLimitCounter counter = counterCache.get(key);
            long currentTime = System.currentTimeMillis();
            long windowMillis = timeUnit.toMillis(duration);

            synchronized (counter) {
                if (currentTime - counter.getWindowStartTime() > windowMillis) {
                    return count;
                }
                return Math.max(0, count - counter.getCount());
            }
        } catch (Exception e) {
            return count;
        }
    }

    /**
     * 获取重置时间（毫秒）
     */
    public long getResetTime(String key, int duration, TimeUnit timeUnit) {
        try {
            RateLimitCounter counter = counterCache.get(key);
            long windowMillis = timeUnit.toMillis(duration);
            return counter.getWindowStartTime() + windowMillis;
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    /**
     * 限流计数器内部类
     */
    private static class RateLimitCounter {
        private AtomicInteger count = new AtomicInteger(0);
        private long windowStartTime = System.currentTimeMillis();

        public int getCount() {
            return count.get();
        }

        public void setCount(int value) {
            count.set(value);
        }

        public void increment() {
            count.incrementAndGet();
        }

        public long getWindowStartTime() {
            return windowStartTime;
        }

        public void setWindowStartTime(long windowStartTime) {
            this.windowStartTime = windowStartTime;
        }
    }
}