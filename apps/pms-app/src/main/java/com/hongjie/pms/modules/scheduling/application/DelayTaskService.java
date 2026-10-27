package com.hongjie.pms.modules.scheduling.application;

import com.hongjie.pms.modules.scheduling.infrastructure.persistence.mapper.DelayTaskMapper;
import com.hongjie.pms.modules.scheduling.infrastructure.persistence.entity.DelayTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class DelayTaskService {

    @Autowired private DelayTaskMapper delayTaskMapper;

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
