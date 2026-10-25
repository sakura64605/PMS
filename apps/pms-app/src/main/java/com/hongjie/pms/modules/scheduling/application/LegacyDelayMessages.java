package com.hongjie.pms.modules.scheduling.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.platform.mq.DelayMessageDto;
import com.hongjie.pms.modules.scheduling.infrastructure.persistence.entity.DelayTask;
import com.hongjie.pms.modules.scheduling.infrastructure.persistence.mapper.DelayTaskMapper;
import com.hongjie.pms.modules.activity.infrastructure.persistence.entity.Activity;
import com.hongjie.pms.modules.activity.infrastructure.persistence.mapper.ActivityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Set;

/** Bridges queued legacy messages into the single persisted XXL-JOB execution path. */
@Service
@RequiredArgsConstructor
public class LegacyDelayMessages {
    private final ScheduledTaskMapper locks;
    private final ActivityMapper activities;
    private final DelayTaskMapper tasks;
    private static final Set<String> TYPES = Set.of("ACTIVITY_REMIND", "ACTIVITY_STATISTICS");

    @Transactional
    public void accept(DelayMessageDto message) {
        if (message == null || message.getType() == null || !TYPES.contains(message.getType())
                || message.getBusinessId() == null || message.getBusinessId() <= 0) {
            throw new IllegalArgumentException("Invalid legacy activity task");
        }
        Long id = message.getBusinessId();
        // Same lock ordering as ScheduledTaskWorker: business row before task row.
        if (locks.lockActivity(id) == null) return;
        Activity activity = activities.selectById(id);
        LocalDateTime time = "ACTIVITY_REMIND".equals(message.getType())
                ? activity.getStartTime().minusMinutes(30) : activity.getEndTime().plusMinutes(1);
        Long existing = tasks.selectCount(new LambdaQueryWrapper<DelayTask>()
                .eq(DelayTask::getTaskType, message.getType()).eq(DelayTask::getBusinessId, id)
                .eq(DelayTask::getExecuteTime, time).in(DelayTask::getStatus, 0, 1));
        if (existing > 0) return;
        DelayTask task = new DelayTask();
        task.setTaskType(message.getType());
        task.setBusinessId(id);
        task.setExecuteTime(time);
        task.setStatus(0);
        task.setCreateTime(LocalDateTime.now());
        tasks.insert(task);
    }
}
