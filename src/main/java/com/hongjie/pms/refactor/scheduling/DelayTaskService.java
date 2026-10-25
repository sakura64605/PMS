package com.hongjie.pms.refactor.scheduling;

import com.hongjie.pms.common.punishment.scheduler.DelayTaskMapper;
import com.hongjie.pms.common.punishment.scheduler.DelayTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class DelayTaskService {

    @Autowired
    private DelayTaskMapper delayTaskMapper;

    @Transactional
    public void addTask(String taskType, Long businessId, LocalDateTime executeTime) {
        DelayTask task = new DelayTask();
        task.setTaskType(taskType);
        task.setBusinessId(businessId);
        task.setExecuteTime(executeTime);
        task.setStatus(0);
        delayTaskMapper.insert(task);
    }
}
