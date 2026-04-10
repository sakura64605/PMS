package com.hongjie.pms.common.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 限流注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 限流key，支持SpEL表达式
     */
    String key() default "";

    /**
     * 时间窗口内允许的请求次数
     */
    int count() default 10;

    /**
     * 时间窗口长度
     */
    int duration() default 1;

    /**
     * 时间单位（秒、分、时、天）
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 是否针对用户限流
     */
    boolean perUser() default true;

    /**
     * 提示消息
     */
    String message() default "请求过于频繁，请稍后再试";
}