package com.hongjie.pms.refactor.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DueTaskBatch {
    private final ScheduledTaskMapper tasks;
    private final ScheduledTaskWorker worker;
    private final SchedulingProperties settings;

    public void execute() {
        LocalDateTime now = LocalDateTime.now();
        long cursor = 0; int completed = 0; int failed = 0;
        int size = Math.max(1, Math.min(500, settings.getBatchSize()));
        int batches = Math.max(1, Math.min(100, settings.getMaxBatches()));
        for (int batch = 0; batch < batches; batch++) {
            var ids = tasks.due(now, cursor, size);
            if (ids.isEmpty()) break;
            for (Long id : ids) {
                cursor = id;
                try { if (worker.execute(id, now)) completed++; }
                catch (RuntimeException failure) {
                    failed++;
                    log.error("到期任务失败，保留待执行状态: taskId={}", id, failure);
                }
            }
            if (ids.size() < size) break;
        }
        log.info("到期任务处理完成: completed={}, failed={}", completed, failed);
        if (failed > 0) throw new IllegalStateException("Due task failures: " + failed + "; see application log");
    }
}
