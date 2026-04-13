package com.hongjie.pms.common.trace;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class TraceAspect {
    
    /**
     * 自动为 Service 层方法埋点
     */
    @Around("execution(* com.hongjie.pms.modules..*ServiceImpl.*(..))")
    public Object traceService(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        String spanName = className + "." + methodName;
        
        // 开始 Span
        TraceContext.startSpan(spanName);
        long start = System.currentTimeMillis();
        
        try {
            Object result = point.proceed();
            return result;
        } finally {
            long cost = System.currentTimeMillis() - start;
            TraceContext.endSpan();
            
            // 慢方法告警
            if (cost > 1000) {
                log.warn("慢方法告警: {} 耗时 {}ms, traceId={}", 
                    spanName, cost, TraceContext.getTraceId());
            }
        }
    }
}