package com.hongjie.pms.common.aspect;

import com.hongjie.pms.common.annotation.DistributedCacheable;
import com.hongjie.pms.common.exception.BusinessException;
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

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
@Order(1)
public class DistributedCacheAspect {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private RedissonClient redissonClient;
    
    private final SpelExpressionParser spelParser = new SpelExpressionParser();
    
    @Around("@annotation(cacheable)")
    public Object around(ProceedingJoinPoint point, DistributedCacheable cacheable) throws Throwable {
        // 1. 生成缓存key
        String cacheKey = generateCacheKey(cacheable, point);
        
        // 2. 先查缓存
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            // 处理空值标记
            if ("NULL".equals(cached)) {
                return null;
            }
            log.debug("缓存命中: key={}", cacheKey);
            return cached;
        }
        
        // 3. 分布式锁（防缓存击穿）
        String lockKey = "lock:cache:" + cacheKey;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 尝试获取锁
            boolean locked = lock.tryLock(cacheable.lockWaitTime(), cacheable.lockLeaseTime(), TimeUnit.SECONDS);
            
            if (locked) {
                // 双重检查
                cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    if ("NULL".equals(cached)) {
                        return null;
                    }
                    return cached;
                }
                
                // 执行目标方法
                Object result = point.proceed();
                
                // 缓存结果
                if (result != null) {
                    redisTemplate.opsForValue().set(cacheKey, result, cacheable.ttl(), TimeUnit.SECONDS);
                    log.debug("缓存写入: key={}, ttl={}s", cacheKey, cacheable.ttl());
                } else if (cacheable.cacheNull()) {
                    // 缓存空值（防穿透）
                    redisTemplate.opsForValue().set(cacheKey, "NULL", cacheable.nullTtl(), TimeUnit.SECONDS);
                    log.debug("缓存空值: key={}, ttl={}s", cacheKey, cacheable.nullTtl());
                }
                
                return result;
            } else {
                // 获取锁失败，等待后重试
                log.debug("获取锁失败，等待重试: key={}", cacheKey);
                Thread.sleep(100);
                return around(point, cacheable);
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
    
    /**
     * 生成缓存key
     */
    private String generateCacheKey(DistributedCacheable cacheable, ProceedingJoinPoint point) {
        String value = cacheable.value();
        String keySpel = cacheable.key();
        
        // 解析SpEL
        String parsedKey = parseSpel(keySpel, point);
        
        return value + ":" + parsedKey;
    }
    
    /**
     * 解析SpEL表达式
     */
    private String parseSpel(String spel, ProceedingJoinPoint point) {
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