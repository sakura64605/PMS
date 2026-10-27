package com.hongjie.pms.shared.core.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Evict after commit, so a concurrent reader cannot refill from uncommitted data. */
@Slf4j
public final class AfterCommitCache {
    private AfterCommitCache() {}

    public static void evict(Runnable eviction) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            run(eviction);
                        }
                    });
        } else {
            run(eviction);
        }
    }

    private static void run(Runnable eviction) {
        try {
            eviction.run();
        } catch (RuntimeException failure) {
            log.error("Committed database write succeeded, but cache eviction failed", failure);
        }
    }
}
