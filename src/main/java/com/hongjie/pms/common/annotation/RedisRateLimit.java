package com.hongjie.pms.common.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis令牌桶限流注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RedisRateLimit {

    /**
     * 限流key，支持SpEL表达式
     */
    String key() default "";

    /**
     * 令牌桶容量（最大突发请求数）
     */
    int capacity() default 10;

    /**
     * 令牌填充速率（每秒生成多少个令牌）
     */
    int refillRate() default 10;

    /**
     * 是否针对用户限流
     */
    boolean perUser() default true;

    /**
     * 提示消息
     */
    String message() default "请求过于频繁，请稍后再试";

    int duration() default 1;           // 时间窗口
    TimeUnit timeUnit() default TimeUnit.SECONDS;  // 时间单位
}