package com.hongjie.pms.common.aspect;

import com.hongjie.pms.common.annotation.DistributedCacheable;
import com.hongjie.pms.common.utils.BloomFilterService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Aspect
@Component
@Order(99)
@Deprecated
public class DistributedCacheAspect {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired(required = false)
    private BloomFilterService bloomFilter;

    private final SpelExpressionParser spelParser = new SpelExpressionParser();

    private static final String NULL_VALUE = "NULL";
    private final ThreadLocal<AtomicInteger> retryCount = ThreadLocal.withInitial(() -> new AtomicInteger(0));

    @Around("@annotation(cacheable)")
    public Object around(ProceedingJoinPoint point, DistributedCacheable cacheable) throws Throwable {
        try {
            retryCount.get().set(0);
            return executeWithCache(point, cacheable);
        } finally {
            retryCount.remove();
        }
    }

    private Object executeWithCache(ProceedingJoinPoint point, DistributedCacheable cacheable) throws Throwable {

        // ==================== 第一层：布隆过滤器（防穿透）- 只对用户查询生效 ====================
        if (cacheable.bloomFilter() && bloomFilter != null) {
            Long userId = extractUserId(point);
            if (userId != null && userId > 0) {
                if (!bloomFilter.userExists(userId)) {
                    log.info("布隆过滤器拦截: userId={}", userId);
                    return null;
                }
                log.debug("布隆过滤器放行: userId={}", userId);
            }
        }

        // 生成缓存key
        String cacheKey = generateCacheKey(cacheable, point);

        // ==================== 第二层：查缓存 ====================
        Object cached = getFromCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        // ==================== 第三层：分布式锁（防击穿） ====================
        String lockKey = "lock:cache:" + cacheKey;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean locked = lock.tryLock(cacheable.lockWaitTime(), cacheable.lockLeaseTime(), TimeUnit.SECONDS);

            if (locked) {
                return executeWithLock(point, cacheable, cacheKey);
            } else {
                return handleLockFailure(point, cacheable);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取分布式锁被中断: key={}", cacheKey);
            return point.proceed();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 从方法参数中提取用户ID
     */
    private Long extractUserId(ProceedingJoinPoint point) {
        Object[] args = point.getArgs();
        if (args == null || args.length == 0) {
            return null;
        }

        // 常见参数名：userId, id, uid
        MethodSignature signature = (MethodSignature) point.getSignature();
        String[] paramNames = signature.getParameterNames();

        for (int i = 0; i < paramNames.length; i++) {
            String paramName = paramNames[i];
            Object arg = args[i];

            if (arg instanceof Long) {
                // 参数名包含user或id
                if (paramName.toLowerCase().contains("user") ||
                        paramName.equalsIgnoreCase("id") ||
                        paramName.equalsIgnoreCase("uid")) {
                    return (Long) arg;
                }
            }
            if (arg instanceof Integer) {
                if (paramName.toLowerCase().contains("user") || paramName.equalsIgnoreCase("id")) {
                    return ((Integer) arg).longValue();
                }
            }
        }

        // 兜底：取第一个Long类型的参数
        for (Object arg : args) {
            if (arg instanceof Long) {
                return (Long) arg;
            }
            if (arg instanceof Integer) {
                return ((Integer) arg).longValue();
            }
        }

        return null;
    }

    private Object executeWithLock(ProceedingJoinPoint point, DistributedCacheable cacheable, String cacheKey) throws Throwable {
        Object cached = getFromCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        long startTime = System.currentTimeMillis();

        try {
            Object result = point.proceed();

            long executeTime = System.currentTimeMillis() - startTime;
            if (executeTime > 100) {
                log.debug("目标方法执行耗时: key={}, 耗时={}ms", cacheKey, executeTime);
            }

            if (result != null) {
                redisTemplate.opsForValue().set(cacheKey, result, cacheable.ttl(), TimeUnit.SECONDS);
                log.debug("缓存写入: key={}, ttl={}s", cacheKey, cacheable.ttl());
            } else if (cacheable.cacheNull()) {
                redisTemplate.opsForValue().set(cacheKey, NULL_VALUE, cacheable.nullTtl(), TimeUnit.SECONDS);
                log.debug("空值缓存写入: key={}, ttl={}s", cacheKey, cacheable.nullTtl());
            }

            return result;

        } catch (Exception e) {
            log.error("执行目标方法失败: key={}", cacheKey, e);
            throw e;
        }
    }

    private Object handleLockFailure(ProceedingJoinPoint point, DistributedCacheable cacheable) throws Throwable {
        int currentRetry = retryCount.get().incrementAndGet();

        if (cacheable.allowRetry() && currentRetry <= cacheable.maxRetries()) {
            log.debug("获取锁失败，第{}次重试", currentRetry);

            long waitTime = Math.min(100 * (1 << (currentRetry - 1)), 1000);
            Thread.sleep(waitTime);

            return executeWithCache(point, cacheable);
        }

        log.warn("获取锁失败，超过最大重试次数，直接查库");
        return point.proceed();
    }

    private Object getFromCache(String cacheKey) {
        Object cached = redisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            if (NULL_VALUE.equals(cached)) {
                log.debug("空值缓存命中: key={}", cacheKey);
                return null;
            }
            log.debug("缓存命中: key={}", cacheKey);
            return cached;
        }

        return null;
    }

    private String generateCacheKey(DistributedCacheable cacheable, ProceedingJoinPoint point) {
        String value = cacheable.value();
        String keySpel = cacheable.key();

        String parsedKey = parseSpel(keySpel, point);

        if (!StringUtils.hasText(parsedKey)) {
            parsedKey = "default";
        }

        return value + ":" + parsedKey;
    }

    private String parseSpel(String spel, ProceedingJoinPoint point) {
        if (!StringUtils.hasText(spel)) {
            return "";
        }

        try {
            MethodSignature signature = (MethodSignature) point.getSignature();
            String[] paramNames = signature.getParameterNames();
            Object[] args = point.getArgs();

            EvaluationContext context = new StandardEvaluationContext();
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }

            Expression expression = spelParser.parseExpression(spel);
            Object value = expression.getValue(context);
            return value != null ? value.toString() : "";

        } catch (Exception e) {
            log.warn("SpEL解析失败: {}", spel, e);
            return spel;
        }
    }
}