package com.hongjie.pms.platform.cache.core;

@FunctionalInterface
public interface CacheLoader<T> {

    /**
     * 加载缓存
     */
    T load();
}
