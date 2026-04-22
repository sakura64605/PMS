package com.hongjie.pms.common.idempotent.aspect;

import com.hongjie.pms.common.idempotent.annotation.Idempotent;
import com.hongjie.pms.common.exception.RepeatConsumptionException;
import com.hongjie.pms.common.idempotent.handler.IdempotentExecuteHandler;
import com.hongjie.pms.common.idempotent.handler.IdempotentExecuteHandlerFactory;
import com.hongjie.pms.common.utils.SecurityUtils;
import com.hongjie.pms.common.idempotent.handler.IdempotentContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
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

@Aspect
@Slf4j
public class IdempotentAspect {

    private final SpelExpressionParser spelParser = new SpelExpressionParser();

    @Around("@annotation(com.hongjie.pms.common.idempotent.annotation.Idempotent)")
    public Object idempotentHandler(ProceedingJoinPoint joinPoint) throws Throwable {
        Idempotent idempotent = getIdempotent(joinPoint);
        // 添加日志，查看注解参数
        log.info("幂等配置 - key: {}, type: {}, perUser: {}, scene: {}",
                idempotent.key(), idempotent.type(), idempotent.perUser(), idempotent.scene());

        // 构建 key
        String key = buildKey(joinPoint, idempotent);
        log.info("构建的幂等 key: {}", key);
        IdempotentExecuteHandler instance = IdempotentExecuteHandlerFactory.getInstance(idempotent.scene(), idempotent.type());
        Object resultObj;
        try {
            instance.execute(joinPoint, idempotent);
            resultObj = joinPoint.proceed();
            instance.postProcessing();
        } catch (RepeatConsumptionException ex) {
            /**
             * 触发幂等逻辑时可能有两种情况：
             *    * 1. 消息还在处理，但是不确定是否执行成功，那么需要返回错误，方便 RocketMQ 再次通过重试队列投递
             *    * 2. 消息处理成功了，该消息直接返回成功即可
             */
            if (!ex.getError()) {
                return null;
            }
            throw ex;
        } catch (Throwable ex) {
            // 客户端消费存在异常，需要删除幂等标识方便下次 RocketMQ 再次通过重试队列投递
            instance.exceptionProcessing();
            throw ex;
        } finally {
            IdempotentContext.clean();
        }
        return resultObj;
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

    public static Idempotent getIdempotent(ProceedingJoinPoint joinPoint) throws NoSuchMethodException {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = joinPoint.getTarget().getClass().getDeclaredMethod(signature.getName(), signature.getParameterTypes());
        return method.getAnnotation(Idempotent.class);
    }
}
