package com.hongjie.pms.common.punishment.scheduler;

/**
 * 延迟任务执行器（SPI）。
 *
 * <p><b>为什么这个接口定义在「老包」里而不是重构层</b>：
 * {@code DelayTaskScheduler} 是 {@code delay_task} 表唯一的派发者，
 * 新增的任务类型必须经由它派发。而它位于 {@code common/punishment/scheduler}，
 * 属于本次重构<b>不动</b>的老代码。
 *
 * <p>如果让调度器去引用重构层（{@code refactor/}）的注册表，就会形成
 * 「老代码反向依赖新代码」，那样「删掉 refactor 目录即可整体回滚」这条底线
 * 立刻失效 —— 老代码会编译不过。所以接口放在调度器所在的包里，
 * <b>实现类放在重构层</b>：依赖方向仍然是「新代码引用老代码」，与
 * {@code PetPostService} 的做法一致。
 *
 * <p>放一个新接口进老包确实略显别扭，但代价是把边界保住了：
 * 删除 {@code refactor/} 后本接口没有实现类，调度器照常编译、老任务类型照常执行。
 *
 * @see DelayTaskScheduler
 */
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
