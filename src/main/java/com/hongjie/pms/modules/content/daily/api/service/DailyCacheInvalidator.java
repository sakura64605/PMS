package com.hongjie.pms.modules.content.daily.api.service;

import com.hongjie.pms.platform.utils.RedisScanUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Daily DTO pages contain counters and viewer state; writes affect every viewer. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyCacheInvalidator {
    private final RedisScanUtil scan;
    private final RedisTemplate<String, Object> redis;

    public void allAfterCommit() {
        afterCommit(this::evictAll);
    }

    /** Called directly by AFTER_COMMIT event listeners. */
    public void evictAll() {
        run(() -> scan.scanAndDelete("daily:rec:*"));
        run(() -> scan.scanAndDelete("rec:daily:*"));
        run(() -> redis.delete("topic:hot"));
    }

    public void viewerAfterCommit(Long userId) {
        afterCommit(() -> {
            run(() -> scan.scanAndDelete("daily:rec:" + userId + ":*"));
            run(() -> scan.scanAndDelete("rec:daily:" + userId + ":*"));
        });
    }

    private void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { task.run(); }
            });
        } else {
            task.run();
        }
    }

    private void run(Runnable task) {
        try { task.run(); }
        catch (RuntimeException e) { log.error("Daily write committed but cache eviction failed", e); }
    }
}
