package com.hongjie.pms.common.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 分布式缓存注解（含分布式锁防击穿 + 布隆过滤器防穿透）
 *
 * @author hongjie
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedCacheable {

    /** 缓存名称 */
    String value();

    /** 缓存key，支持SpEL */
    String key();

    /** 正常数据过期时间（秒） */
    long ttl() default 3600;

    /** 空值过期时间（秒）- 防穿透第二层 */
    long nullTtl() default 60;

    /** 是否缓存null值 - 防穿透第二层 */
    boolean cacheNull() default true;

    /** 分布式锁等待时间（秒）- 防击穿 */
    long lockWaitTime() default 3;

    /** 分布式锁持有时间（秒）- 防击穿 */
    long lockLeaseTime() default 10;

    /** 是否启用布隆过滤器 - 防穿透第一层（仅对用户查询生效） */
    boolean bloomFilter() default false;

    /** 是否允许递归重试（获取锁失败时） */
    boolean allowRetry() default true;

    /** 最大重试次数 */
    int maxRetries() default 3;
}