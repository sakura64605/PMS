package com.hongjie.pms.refactor.activity;

import com.hongjie.pms.common.cache.DistributedCache;
import com.hongjie.pms.common.utils.RedisScanUtil;
import com.hongjie.pms.modules.activity.cache.ActivityCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;
import java.util.Set;
import static org.mockito.Mockito.*;

class ActivityCacheTest {
    DistributedCache cache = mock(DistributedCache.class);
    RedisScanUtil scan = mock(RedisScanUtil.class);
    ActivityCache activities = new ActivityCache(cache, scan);

    @AfterEach void cleanup() { TransactionSynchronizationManager.clear(); }

    @Test void deletesExactKeysAndAllPagesThroughDistributedCache() {
        var pages = Set.of("activityList_1_10_0", "activityList_2_20_city_date");
        var users = Set.of("userActivity_11", "userActivity_12");
        when(scan.scanKeys("activityList_*")).thenReturn(pages);
        when(scan.scanKeys("userActivity_*")).thenReturn(users);
        activities.evictAfterCommit(7L);
        verify(cache).delete("activity_7");
        verify(cache).delete("activity:fallback_7");
        verify(cache).delete(pages);
        verify(cache).delete(users);
    }

    @Test void waitsUntilCommit() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        activities.evictAfterCommit(7L);
        verifyNoInteractions(cache, scan);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(cache).delete("activity_7");
    }

    @Test void rollbackLeavesCacheUntouched() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        activities.evictAfterCommit(7L);
        TransactionSynchronizationManager.getSynchronizations().forEach(
                sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(cache, scan);
    }
}
