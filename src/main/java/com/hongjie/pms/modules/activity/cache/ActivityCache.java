package com.hongjie.pms.modules.activity.cache;

import com.hongjie.pms.common.cache.DistributedCache;
import com.hongjie.pms.common.cache.toolkit.CacheUtil;
import com.hongjie.pms.common.utils.RedisScanUtil;
import com.hongjie.pms.refactor.core.cache.AfterCommitCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Shared invalidation for activity mutations and after-commit event listeners. */
@Component
@RequiredArgsConstructor
public class ActivityCache {
    private final DistributedCache cache;
    private final RedisScanUtil scan;

    public void evictAfterCommit(Long activityId) {
        AfterCommitCache.evict(() -> evict(activityId));
    }

    /** Immediate eviction for listeners already executing AFTER_COMMIT. */
    public void evict(Long activityId) {
        cache.delete(CacheUtil.buildKey("activity", String.valueOf(activityId)));
        cache.delete(CacheUtil.buildKey("activity:fallback", String.valueOf(activityId)));
        evictMatching("activityList_*");
        evictMatching("userActivity_*");
    }

    private void evictMatching(String pattern) {
        var keys = scan.scanKeys(pattern);
        if (!keys.isEmpty()) {
            cache.delete(keys);
        }
    }
}
