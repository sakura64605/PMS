package com.hongjie.pms.common.aspect;

import com.hongjie.pms.common.annotation.Idempotent;
import com.hongjie.pms.common.exception.BusinessException;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
@Order(2)
public class IdempotentAspect {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    private final SpelExpressionParser spelParser = new SpelExpressionParser();
    
    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint point, Idempotent idempotent) throws Throwable {

        // 构建幂等key
        String key = buildKey(point, idempotent);
        
        // 尝试获取锁
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1");
        
        if (acquired == null || !acquired) {
            String requestUri = getRequestUri();
            log.warn("幂等拦截 - key: {}, uri: {}", key, requestUri);
            throw new BusinessException(409, idempotent.message());
        }
        
        // 设置过期时间
        redisTemplate.expire(key, idempotent.expire(), idempotent.timeUnit());
        
        try {
            log.debug("幂等放行 - key: {}", key);
            return point.proceed();
        } finally {
            // 操作完成后删除key
            redisTemplate.delete(key);
        }
    }
    
    /**
     * 构建幂等key
     */
    private String buildKey(ProceedingJoinPoint point, Idempotent idempotent) {
        StringBuilder keyBuilder = new StringBuilder("idempotent:");
        
        // 添加类名和方法名
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        keyBuilder.append(method.getDeclaringClass().getSimpleName())
                  .append(":")
                  .append(method.getName());
        
        // 解析SpEL表达式
        if (StringUtils.hasText(idempotent.key())) {
            String spelValue = parseSpelExpression(idempotent.key(), point, signature);
            if (StringUtils.hasText(spelValue)) {
                keyBuilder.append(":").append(spelValue);
            }
        }
        
        // 添加用户标识
        if (idempotent.perUser()) {
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
    private String parseSpelExpression(String spelExpression, ProceedingJoinPoint point, MethodSignature signature) {
        try {
            Expression expression = spelParser.parseExpression(spelExpression);
            EvaluationContext context = new StandardEvaluationContext();
            
            // 添加方法参数
            Object[] args = point.getArgs();
            String[] parameterNames = signature.getParameterNames();
            for (int i = 0; i < args.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
            
            Object value = expression.getValue(context);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.warn("解析SpEL表达式失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取当前用户ID
     */
    private Long getCurrentUserId() {
        try {
            return SecurityUtils.getCurrentUserId();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 获取客户端IP
     */
    private String getClientIp() {
        try {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
                // 多次反向代理后会有多个IP值，第一个为真实IP
                int index = ip.indexOf(",");
                if (index != -1) {
                    return ip.substring(0, index);
                } else {
                    return ip;
                }
            }
            ip = request.getHeader("X-Real-IP");
            if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    /**
     * 获取请求URI
     */
    private String getRequestUri() {
        try {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            return request.getRequestURI();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
