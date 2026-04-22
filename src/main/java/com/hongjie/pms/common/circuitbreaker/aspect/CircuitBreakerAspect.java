package com.hongjie.pms.common.circuitbreaker.aspect;

import com.hongjie.pms.common.circuitbreaker.annotation.CircuitBreaker;
import com.hongjie.pms.common.circuitbreaker.CircuitBreakerConfig;
import com.hongjie.pms.common.circuitbreaker.CircuitBreakerManager;
import jakarta.annotation.PostConstruct;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Slf4j
@Aspect
@Order(2)
@NoArgsConstructor
public class CircuitBreakerAspect {

    @Autowired
    private CircuitBreakerManager breakerManager;

    @PostConstruct
    public void init() {
        log.info("========== CircuitBreakerAspect 已加载 ==========");
    }

    @Around("@annotation(circuitBreaker)")
    public Object around(ProceedingJoinPoint point, CircuitBreaker circuitBreaker) throws Throwable {
        log.info("CircuitBreakerAspect被触发");
        MethodSignature signature = (MethodSignature) point.getSignature();
        String resourceName = circuitBreaker.value();
        if (resourceName.isEmpty()) {
            resourceName = signature.getDeclaringTypeName() + "." + signature.getName();
        }
        log.info("资源名称: {}", resourceName);

        // 1. 创建熔断器配置
        CircuitBreakerConfig config = new CircuitBreakerConfig();
        config.setWindowSize(circuitBreaker.windowSize());
        config.setMinRequestAmount(circuitBreaker.minRequestAmount());
        config.setErrorRateThreshold(circuitBreaker.errorRateThreshold());
        config.setOpenDurationSeconds(circuitBreaker.openDurationSeconds());

        // 2. 获取熔断器
        com.hongjie.pms.common.circuitbreaker.CircuitBreaker breaker =
                breakerManager.getOrCreate(resourceName, config);

        // 3. 设置降级方法
        String fallbackMethodName = circuitBreaker.fallbackMethod();
        if (!fallbackMethodName.isEmpty()) {
            breaker.withFallback(e -> {
                return invokeFallbackMethod(point, fallbackMethodName, e);
            });
        }

        // 4. 执行
        return breaker.execute(
                () -> {
                    try {
                        return point.proceed();
                    } catch (Throwable ex) {
                        throw new Exception(ex);
                    }
                },
                Object.class
        );
    }

    /**
     * 调用降级方法
     */
    private Object invokeFallbackMethod(ProceedingJoinPoint point, String fallbackMethodName, Exception e) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        Object target = point.getTarget();
        Object[] args = point.getArgs();

        try {
            // 方式1：查找相同参数类型的降级方法
            Method fallbackMethod = target.getClass().getDeclaredMethod(fallbackMethodName, method.getParameterTypes());
            fallbackMethod.setAccessible(true);
            return fallbackMethod.invoke(target, args);

        } catch (NoSuchMethodException ex1) {
            // 方式2：查找带异常参数的降级方法
            try {
                Class<?>[] paramTypes = new Class[method.getParameterCount() + 1];
                System.arraycopy(method.getParameterTypes(), 0, paramTypes, 0, method.getParameterCount());
                paramTypes[paramTypes.length - 1] = Exception.class;

                Method fallbackWithEx = target.getClass().getDeclaredMethod(fallbackMethodName, paramTypes);
                fallbackWithEx.setAccessible(true);

                Object[] newArgs = new Object[args.length + 1];
                System.arraycopy(args, 0, newArgs, 0, args.length);
                newArgs[args.length] = e;

                return fallbackWithEx.invoke(target, newArgs);

            } catch (NoSuchMethodException ex2) {
                log.error("找不到降级方法: {}，请确保方法存在", fallbackMethodName);
                return null;
            } catch (IllegalAccessException | InvocationTargetException ex2) {
                log.error("调用降级方法失败: {}", fallbackMethodName, ex2);
                return null;
            }

        } catch (IllegalAccessException | InvocationTargetException ex1) {
            log.error("调用降级方法失败: {}", fallbackMethodName, ex1);
            return null;
        }
    }
}