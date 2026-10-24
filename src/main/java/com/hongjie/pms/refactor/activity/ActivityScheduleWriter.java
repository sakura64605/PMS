package com.hongjie.pms.refactor.activity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.common.punishment.scheduler.DelayTask;
import com.hongjie.pms.common.punishment.scheduler.DelayTaskMapper;
import com.hongjie.pms.common.punishment.scheduler.DelayTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

/** Participates in the content transaction; other business tasks are never removed. */
@Component
@RequiredArgsConstructor
public class ActivityScheduleWriter {
    private final DelayTaskMapper tasks;
    private final DelayTaskService scheduler;

    @Transactional(propagation = Propagation.MANDATORY)
    public void replace(Long id, LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        cancel(id);
        LocalDateTime reminder = start.minusMinutes(30);
        if (reminder.isAfter(now)) scheduler.addTask("ACTIVITY_REMIND", id, reminder);
        scheduler.addTask("ACTIVITY_START", id, start);
        scheduler.addTask("ACTIVITY_STATISTICS", id, end.plusMinutes(1));
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancel(Long id) {
        tasks.delete(new LambdaQueryWrapper<DelayTask>()
                .eq(DelayTask::getBusinessId, id).eq(DelayTask::getStatus, 0)
                .in(DelayTask::getTaskType, List.of("ACTIVITY_REMIND", "ACTIVITY_START", "ACTIVITY_STATISTICS")));
    }
}
