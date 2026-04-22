package com.hongjie.pms.common.handler;

import cn.hutool.core.collection.CollUtil;

import java.util.HashMap;
import java.util.Map;

public final class IdempotentContext {

    private static final ThreadLocal<Map<String, Object>> CONTEXT = new ThreadLocal<>();

    public static Map<String, Object> get() {
        return CONTEXT.get();
    }

    public static Object getKey(String key) {
        Map<String, Object> context = get();
        if (CollUtil.isNotEmpty(context)) {
            return context.get(key);
        }
        return null;
    }

    public static void put(String key, Object value) {
        Map<String, Object> context = get();
        if (CollUtil.isEmpty(context)){
            context = new HashMap<>();
        }
        context.put(key, value);
        putContext(context);
    }

    private static void putContext(Map<String, Object> context) {
        Map<String, Object> threadContext = CONTEXT.get();
        if (CollUtil.isNotEmpty(threadContext)) {
            threadContext.putAll(context);
            return;
        }
        CONTEXT.set(context);
    }

}
