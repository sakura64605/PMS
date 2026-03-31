package com.hongjie.pms.common.base.core;

public class UpdateTimeContext {

    private static final ThreadLocal<Boolean> SKIP = ThreadLocal.withInitial(() -> false);

    public static void skip() {
        SKIP.set(true);
    }

    public static boolean shouldSkip() {
        return SKIP.get();
    }

    public static void clear() {
        SKIP.remove();
    }

}
