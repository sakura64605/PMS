package com.hongjie.pms.common.delay;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@EnableScheduling
public class DelayTaskScheduler {
    
    @Autowired
    private DelayTaskMapper delayTaskMapper;
    @Autowired
    private DelayTaskHandler taskHandler;
    
    @Scheduled(fixedDelay = 60000)  // 每分钟扫描一次
    public void execute() {
        List<DelayTask> tasks = delayTaskMapper.selectList(
            new LambdaQueryWrapper<DelayTask>()
                .eq(DelayTask::getStatus, 0)
                .le(DelayTask::getExecuteTime, LocalDateTime.now())
        );
        
        for (DelayTask task : tasks) {
            try {
                if ("ACTIVITY_REMIND".equals(task.getTaskType())) {
                    taskHandler.handleActivityRemind(task.getBusinessId());
                } else if ("ACTIVITY_STATISTICS".equals(task.getTaskType())) {
                    taskHandler.handleActivityStatistics(task.getBusinessId());
                }
                task.setStatus(1);
                delayTaskMapper.updateById(task);
                log.info("延时任务执行成功: id={}", task.getId());
            } catch (Exception e) {
                log.error("延时任务执行失败: id={}", task.getId(), e);
            }
        }
    }
}