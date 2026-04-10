package com.hongjie.pms.common.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 分布式缓存注解（含分布式锁防击穿）
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedCacheable {
    
    /** 缓存名称 */
    String value();
    
    /** 缓存key，支持SpEL */
    String key();
    
    /** 过期时间（秒） */
    long ttl() default 3600;
    
    /** 空值过期时间（秒） */
    long nullTtl() default 60;
    
    /** 是否缓存null值（防穿透） */
    boolean cacheNull() default true;
    
    /** 分布式锁等待时间（秒） */
    long lockWaitTime() default 3;
    
    /** 锁持有时间（秒） */
    long lockLeaseTime() default 10;
}