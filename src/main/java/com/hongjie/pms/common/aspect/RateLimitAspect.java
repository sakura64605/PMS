package com.hongjie.pms.common.aspect;

import com.hongjie.pms.common.annotation.RateLimit;
import com.hongjie.pms.common.exception.RateLimitException;
import com.hongjie.pms.common.utils.RateLimiterManager;
import com.hongjie.pms.common.utils.SecurityUtils;
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

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
@Order(1)
public class RateLimitAspect {

    @Autowired
    private RateLimiterManager rateLimiterManager;

    private final SpelExpressionParser spelParser = new SpelExpressionParser();

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint point, RateLimit rateLimit) throws Throwable {
        // 构建限流key
        String key = buildKey(point, rateLimit);

        // 获取请求URI
        String requestUri = getRequestUri();

        // 尝试获取许可（使用 count + duration + timeUnit）
        boolean acquired = rateLimiterManager.tryAcquire(
                key,
                rateLimit.count(),
                rateLimit.duration(),
                rateLimit.timeUnit()
        );

        if (!acquired) {
            // 计算剩余时间和剩余次数
            long resetTime = rateLimiterManager.getResetTime(key, rateLimit.duration(), rateLimit.timeUnit());
            int remaining = rateLimiterManager.getRemaining(key, rateLimit.count(), rateLimit.duration(), rateLimit.timeUnit());
            long waitSeconds = Math.max(0, (resetTime - System.currentTimeMillis()) / 1000);

            // 友好的提示消息
            String message = buildRateLimitMessage(rateLimit, waitSeconds, remaining);

            log.warn("限流拦截 - key: {}, count: {}/{}{}, uri: {}",
                    key,
                    rateLimit.count(),
                    rateLimit.duration(),
                    getTimeUnitStr(rateLimit.timeUnit()),
                    requestUri);

            throw new RateLimitException(message, requestUri, key);
        }

        return point.proceed();
    }

    /**
     * 构建友好的限流提示消息
     */
    private String buildRateLimitMessage(RateLimit rateLimit, long waitSeconds, int remaining) {
        String timeDesc = getTimeUnitDesc(rateLimit.duration(), rateLimit.timeUnit());

        if (waitSeconds > 0) {
            if (waitSeconds < 60) {
                return String.format("操作过于频繁，请%d秒后再试", waitSeconds);
            } else if (waitSeconds < 3600) {
                return String.format("操作过于频繁，请%d分钟后再试", waitSeconds / 60);
            } else {
                return String.format("操作过于频繁，请%d小时后再试", waitSeconds / 3600);
            }
        }

        return rateLimit.message();
    }

    /**
     * 获取时间单位字符串
     */
    private String getTimeUnitStr(TimeUnit timeUnit) {
        switch (timeUnit) {
            case SECONDS: return "秒";
            case MINUTES: return "分";
            case HOURS: return "时";
            case DAYS: return "天";
            default: return "";
        }
    }

    /**
     * 获取时间单位描述
     */
    private String getTimeUnitDesc(int duration, TimeUnit timeUnit) {
        switch (timeUnit) {
            case SECONDS:
                return duration + "秒";
            case MINUTES:
                return duration + "分钟";
            case HOURS:
                return duration + "小时";
            case DAYS:
                return duration + "天";
            default:
                return "";
        }
    }

    /**
     * 构建限流key
     */
    private String buildKey(ProceedingJoinPoint point, RateLimit rateLimit) {
        StringBuilder keyBuilder = new StringBuilder("rate_limit:");

        // 添加类名和方法名
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        keyBuilder.append(method.getDeclaringClass().getSimpleName())
                .append(".")
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