package com.hongjie.pms.common.punishment.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 延迟任务调度器：每分钟扫描 {@code delay_task} 表并派发。
 *
 * <p><b>派发顺序：先查执行器，再走老分支。</b>
 * 新增的任务类型实现 {@link DelayTaskExecutor} 并标 {@code @Component} 即可被派发，
 * 不需要改本类；老的那串 {@code if-else} 作为兜底保留。
 *
 * <p>为什么保留老分支而不是一次换掉：那 6 个任务类型
 * （活动提醒、活动统计、爽约惩罚结束、活动开始、禁言结束、禁封结束）
 * 涉及惩罚与活动模块的副作用，而当前环境跑不起完整应用、无法验证。
 * 逐个迁移并验证之后，这段 if-else 就可以删掉了 —— 那时它已经全部由执行器覆盖。
 *
 * <p>注意<b>不认识的任务类型仍然会被标记为已执行</b>（与改动前一致）：
 * 本类会把扫到的任务一律置为 status=1，无论是否找到对应实现。
 * 这是既有行为，此处只是补了一条告警日志，没有改变它 ——
 * 因为改变它会让历史上已经"被吞掉"的任务语义发生变化。
 */
@Component
@Slf4j
@org.springframework.context.annotation.Profile("legacy-reference")
public class DelayTaskScheduler {

    @Autowired
    private DelayTaskMapper delayTaskMapper;
    @Autowired
    private DelayTaskHandler taskHandler;

    /**
     * 任务类型 -> 执行器。
     *
     * <p>构造时由 Spring 注入全部实现并建索引。这里刻意不引入重构层的
     * {@code StrategyRegistry} —— 那会让老包依赖新包。五六行 Map 足够，
     * 边界比复用更重要。
     */
    private final Map<String, DelayTaskExecutor> executors = new HashMap<>();

    @Autowired(required = false)
    public void setExecutors(List<DelayTaskExecutor> delayTaskExecutors) {
        if (delayTaskExecutors == null) {
            return;
        }
        for (DelayTaskExecutor executor : delayTaskExecutors) {
            executors.put(executor.taskType(), executor);
        }
        log.info("延迟任务执行器已注册: {}", executors.keySet());
    }

    public void execute() {
        List<DelayTask> tasks = delayTaskMapper.selectList(
            new LambdaQueryWrapper<DelayTask>()
                .eq(DelayTask::getStatus, 0)
                .le(DelayTask::getExecuteTime, LocalDateTime.now())
        );

        for (DelayTask task : tasks) {
            try {
                if (!dispatch(task)) {
                    log.warn("未知的延迟任务类型，将被直接标记为已执行: type={}, id={}",
                            task.getTaskType(), task.getId());
                }
                task.setStatus(1);
                delayTaskMapper.updateById(task);
                log.info("延时任务执行成功: id={}", task.getId());
            } catch (Exception e) {
                log.error("延时任务执行失败: id={}", task.getId(), e);
            }
        }
    }

    /**
     * 派发一个任务。
     *
     * @return 是否找到了处理者（老分支或执行器）
     */
    private boolean dispatch(DelayTask task) {
        DelayTaskExecutor executor = executors.get(task.getTaskType());
        if (executor != null) {
            executor.execute(task.getBusinessId());
            return true;
        }
        return dispatchLegacy(task);
    }

    /** 老分支兜底：迁移完成后即可整体删除 */
    private boolean dispatchLegacy(DelayTask task) {
        switch (task.getTaskType()) {
            case "ACTIVITY_REMIND" -> taskHandler.handleActivityRemind(task.getBusinessId());
            case "ACTIVITY_STATISTICS" -> taskHandler.handleActivityStatistics(task.getBusinessId());
            case "SIGNUP_PUNISHMENT_END" -> taskHandler.handleSignupEnd(task.getBusinessId());
            case "ACTIVITY_START" -> taskHandler.handleActivityStart(task.getBusinessId());
            case "MUTED_PUNISHMENT_END" -> taskHandler.handleMutedEnd(task.getBusinessId());
            case "BANNED_PUNISHMENT_END" -> taskHandler.handleBannedEnd(task.getBusinessId());
            default -> {
                return false;
            }
        }
        return true;
    }
}
