package com.hongjie.pms.modules.search.application.index;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.*;

@Service
@Slf4j
public class SearchMaintenance {
    public record Status(
            String phase,
            long processed,
            String startedAt,
            String finishedAt,
            String error,
            long incrementalFailures) {}

    private final SearchIndexCoordinator coordinator;
    private final SearchSyncDispatch dispatch;
    private final Executor executor;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong processed = new AtomicLong();
    private volatile String phase = "IDLE", started, finished, error;

    public SearchMaintenance(
            SearchIndexCoordinator coordinator,
            SearchSyncDispatch dispatch,
            @Qualifier("searchMaintenanceExecutor") Executor executor) {
        this.coordinator = coordinator;
        this.dispatch = dispatch;
        this.executor = executor;
    }

    public void request() {
        reserve();
        try {
            executor.execute(
                    () -> {
                        try {
                            execute();
                        } catch (RuntimeException failure) {
                            log.error(
                                    "Search rebuild failed; previous alias is retained unless switch outcome was uncertain",
                                    failure);
                        }
                    });
        } catch (RuntimeException rejected) {
            error = "Rebuild executor unavailable";
            phase = "FAILED";
            finished = Instant.now().toString();
            running.set(false);
            throw rejected;
        }
    }

    public void rebuildNow() {
        reserve();
        execute();
    }

    private void reserve() {
        if (!running.compareAndSet(false, true))
            throw new BusinessException(ErrorCode.PARAM_ERROR, "索引重建正在执行");
        processed.set(0);
        error = null;
        finished = null;
        started = Instant.now().toString();
        phase = "RUNNING";
    }

    private void execute() {
        try {
            coordinator.rebuild(processed::addAndGet);
            phase = "SUCCEEDED";
        } catch (RuntimeException failure) {
            error = "索引重建失败，请检查服务日志及当前别名指向";
            phase = "FAILED";
            throw failure;
        } finally {
            finished = Instant.now().toString();
            running.set(false);
        }
    }

    public Status status() {
        return new Status(phase, processed.get(), started, finished, error, dispatch.failures());
    }
}
