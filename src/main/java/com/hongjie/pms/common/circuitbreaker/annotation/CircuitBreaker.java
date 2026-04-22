package com.hongjie.pms.common.circuitbreaker.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CircuitBreaker {
    
    /** 资源名称 */
    String value() default "";
    
    /** 统计窗口大小（秒） */
    int windowSize() default 10;
    
    /** 最小请求数 */
    int minRequestAmount() default 5;
    
    /** 错误率阈值（0.5 = 50%） */
    double errorRateThreshold() default 0.5;
    
    /** 熔断时长（秒） */
    int openDurationSeconds() default 10;
    
    /** 降级方法名 */
    String fallbackMethod() default "";
}