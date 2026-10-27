package com.hongjie.pms.modules.scheduling.application;

import com.hongjie.pms.modules.scheduling.api.DelayTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ScheduledTaskWorker {
    private final ScheduledTaskMapper tasks;
    private final Map<String, DelayTaskExecutor> handlers = new HashMap<>();

    public ScheduledTaskWorker(ScheduledTaskMapper tasks, List<DelayTaskExecutor> handlers) {
        this.tasks = tasks;
        for (var handler : handlers) {
            if (this.handlers.putIfAbsent(handler.taskType(), handler) != null)
                throw new IllegalStateException(
                        "Duplicate scheduled task handler: " + handler.taskType());
        }
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    public boolean execute(Long id, LocalDateTime now) {
        var candidate = tasks.find(id);
        if (candidate == null || !Integer.valueOf(0).equals(candidate.getStatus())) return false;
        var handler = handlers.get(candidate.getTaskType());
        if (handler == null)
            throw new IllegalStateException("Unknown task type: " + candidate.getTaskType());
        // Editors lock the business row before replacing tasks: keep the same lock order.
        if (candidate.getTaskType().startsWith("ACTIVITY_"))
            tasks.lockActivity(candidate.getBusinessId());
        else if (candidate.getTaskType().endsWith("PUNISHMENT_END"))
            tasks.lockUser(candidate.getBusinessId());
        else if ("NOTICE_PUBLISH".equals(candidate.getTaskType()))
            tasks.lockNotice(candidate.getBusinessId());
        var current = tasks.lockDue(id, now);
        if (current == null) return false;
        if (!Objects.equals(candidate.getTaskType(), current.getTaskType())
                || !Objects.equals(candidate.getBusinessId(), current.getBusinessId()))
            throw new IllegalStateException("Task routing changed while acquiring lock: " + id);
        handler.execute(current.getBusinessId());
        if (tasks.complete(id) != 1)
            throw new IllegalStateException("Task completion conflict: " + id);
        return true;
    }
}
