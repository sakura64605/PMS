package com.hongjie.pms.modules.search.application.index;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.*;

@Component
@Slf4j
public class SearchSyncDispatch {
    private final SearchIndexCoordinator coordinator;
    private final Executor executor;
    private final AtomicLong failures = new AtomicLong();

    public SearchSyncDispatch(
            SearchIndexCoordinator coordinator,
            @Qualifier("searchIndexExecutor") Executor executor) {
        this.coordinator = coordinator;
        this.executor = executor;
    }

    public long failures() {
        return failures.get();
    }

    public void submit(String type, Long id) {
        if (id == null || id <= 0) return;
        Runnable enqueue =
                () -> {
                    try {
                        executor.execute(
                                () -> {
                                    try {
                                        coordinator.sync(type, id);
                                    } catch (RuntimeException failure) {
                                        failed(type, id, failure);
                                    }
                                });
                    } catch (RuntimeException rejected) {
                        failed(type, id, rejected);
                    }
                };
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            enqueue.run();
                        }
                    });
        else enqueue.run();
    }

    private void failed(String type, Long id, RuntimeException failure) {
        failures.incrementAndGet();
        log.error(
                "Search sync failed; reconcile with index rebuild: type={}, id={}",
                type,
                id,
                failure);
    }
}
