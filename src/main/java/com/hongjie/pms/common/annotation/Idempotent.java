package com.hongjie.pms.common.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 幂等注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 幂等key，支持SpEL表达式
     */
    String key() default "";

    /**
     * 过期时间
     */
    int expire() default 60;

    /**
     * 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 提示消息
     */
    String message() default "操作正在处理中，请稍后再试";

    /**
     * 是否针对用户
     */
    boolean perUser() default true;
}
