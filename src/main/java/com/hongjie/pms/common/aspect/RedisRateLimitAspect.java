package com.hongjie.pms.common.aspect;

import com.hongjie.pms.common.annotation.RedisRateLimit;
import com.hongjie.pms.common.exception.RateLimitException;
import com.hongjie.pms.common.utils.RedisRateLimiter;
import com.hongjie.pms.common.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

@Slf4j
@Aspect
@Component
@Order(1)
public class RedisRateLimitAspect {
    
    @Autowired
    private RedisRateLimiter redisRateLimiter;
    
    private final SpelExpressionParser spelParser = new SpelExpressionParser();
    
    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint point, RedisRateLimit rateLimit) throws Throwable {

        log.info("限流参数: capacity={}, refillRate={}", rateLimit.capacity(), rateLimit.refillRate());

        // 构建限流key
        String key = buildKey(point, rateLimit);
        
        // 尝试获取令牌
        boolean acquired = redisRateLimiter.tryAcquire(
            key,
            rateLimit.capacity(),
            rateLimit.refillRate(),
            rateLimit.duration(),
            rateLimit.timeUnit()
        );
        
        if (!acquired) {
            String requestUri = getRequestUri();
            log.warn("Redis限流拦截 - key: {}, capacity: {}, refillRate: {}/s, uri: {}", 
                key, rateLimit.capacity(), rateLimit.refillRate(), requestUri);
            throw new RateLimitException(rateLimit.message(), requestUri, key);
        }
        
        log.debug("Redis限流放行 - key: {}, 剩余令牌: {}", key, redisRateLimiter.getCurrentTokens(key));
        return point.proceed();
    }
    
    /**
     * 构建限流key
     */
    private String buildKey(ProceedingJoinPoint point, RedisRateLimit rateLimit) {
        StringBuilder keyBuilder = new StringBuilder("rate_limit:token_bucket:");
        
        // 添加类名和方法名
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        keyBuilder.append(method.getDeclaringClass().getSimpleName())
                  .append(":")
                  .append(method.getName());
        
        // 解析SpEL表达式
        if (StringUtils.hasText(rateLimit.key())) {
            String spelValue = parseSpelExpression(rateLimit.key(), point, signature);
            if (StringUtils.hasText(spelValue)) {
                keyBuilder.append(":").append(spelValue);
            }
        }
        
        // 添加用户标识
        if (rateLimit.perUser()) {
            Long userId = getCurrentUserId();
            if (userId != null) {
                keyBuilder.append(":user:").append(userId);
            } else {
                keyBuilder.append(":ip:").append(getClientIp());
            }
        }
        
        return keyBuilder.toString();
    }
    
    /**
     * 解析SpEL表达式
     */
    private String parseSpelExpression(String spel, ProceedingJoinPoint point, MethodSignature signature) {
        try {
            EvaluationContext context = new StandardEvaluationContext();
            String[] paramNames = signature.getParameterNames();
            Object[] args = point.getArgs();
            
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
                context.setVariable("p" + i, args[i]);
            }
            
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                context.setVariable("request", attributes.getRequest());
            }
            
            Expression expression = spelParser.parseExpression(spel);
            Object value = expression.getValue(context);
            return value != null ? value.toString() : "";
        } catch (Exception e) {
            log.warn("SpEL表达式解析失败: {}", spel, e.getMessage());
            return "";
        }
    }
    
    private Long getCurrentUserId() {
        try {
            return SecurityUtils.getCurrentUserId();
        } catch (Exception e) {
            return null;
        }
    }
    
    private String getClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return "unknown";
        
        HttpServletRequest request = attributes.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.isEmpty()) ip = request.getRemoteAddr();
        if (ip != null && ip.contains(",")) ip = ip.split(",")[0];
        return ip != null ? ip : "unknown";
    }
    
    private String getRequestUri() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            return request.getMethod() + " " + request.getRequestURI();
        }
        return "unknown";
    }
}