package com.hongjie.pms.common.punishment.scheduler;

/** Shared persisted-task execution contract, dispatched by refactor.scheduling.ScheduledTaskWorker. */
public interface DelayTaskExecutor {

    /** 任务类型码，与 {@code delay_task.task_type} 的取值对应 */
    String taskType();

    /**
     * 执行任务。
     *
     * <p>实现必须是<b>幂等</b>的：延迟任务可能因为重投或兜底补偿被触发多次。
     */
    void execute(Long businessId);
}
