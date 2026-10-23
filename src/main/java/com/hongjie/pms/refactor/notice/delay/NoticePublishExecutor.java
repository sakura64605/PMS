package com.hongjie.pms.refactor.notice.delay;

import com.hongjie.pms.refactor.core.enums.biz.NoticeStatus;
import com.hongjie.pms.common.punishment.scheduler.DelayTaskExecutor;
import com.hongjie.pms.modules.notice.entity.Notice;
import com.hongjie.pms.modules.notice.mapper.NoticeMapper;
import com.hongjie.pms.refactor.core.event.DomainEventPublisher;
import com.hongjie.pms.refactor.notice.event.NoticeChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 定时发布公告 —— 由 {@code delay_task} 表驱动。
 *
 * <p><b>为什么换成这套机制</b>：老实现用 Redisson 的 {@code RDelayedQueue}
 * 加一个 {@code @PostConstruct} 里的裸线程来消费，问题是：
 * <ul>
 *   <li>任务只存在于 Redis 里，<b>数据库中没有等待中的记录</b>，
 *       查不到、改不了、出问题也追溯不了；</li>
 *   <li>那个线程不是守护线程且有 {@code while(true)}，<b>会阻止 JVM 正常退出</b>；</li>
 *   <li>启动时就要拿 Redisson 队列，也就是<b>启动即强依赖 Redis</b>。</li>
 * </ul>
 * 项目里其实已经有现成的延迟任务设施（{@code delay_task} 表 + {@code DelayTaskScheduler}
 * 轮询），惩罚模块一直在用。接进它之后：任务落库可查可观测、不依赖 Redis、
 * 线程模型由 Spring 的调度器管理。本类就是那个设施的一个执行器。
 *
 * <p><b>幂等</b>：延迟任务可能因重投或兜底补偿被触发多次。这里以
 * 「当前状态是否仍为待发布」作为幂等条件 —— 已是发布态则直接返回，
 * 不会重复写库、也不会重复清缓存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticePublishExecutor implements DelayTaskExecutor {

    /** 任务类型码，写入 {@code delay_task.task_type} */
    public static final String TASK_TYPE = "NOTICE_PUBLISH";

    private final NoticeMapper noticeMapper;

    private final DomainEventPublisher eventPublisher;

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Long businessId) {
        Notice notice = noticeMapper.selectById(businessId);
        if (notice == null) {
            log.warn("定时发布公告时找不到公告，可能已被删除: noticeId={}", businessId);
            return;
        }
        // 幂等：已经是发布态就不再处理
        if (NoticeStatus.PUBLISHED.getCode().equals(notice.getStatus())) {
            log.debug("公告已发布，跳过定时发布: noticeId={}", businessId);
            return;
        }

        notice.setStatus(NoticeStatus.PUBLISHED.getCode());
        notice.setPublishTime(LocalDateTime.now());
        noticeMapper.updateById(notice);

        // 缓存清除走事件、在提交后执行（见 NoticeCacheEvictListener）
        eventPublisher.publish(new NoticeChangedEvent(businessId));

        log.info("公告已按定时发布: noticeId={}, title={}", businessId, notice.getTitle());
    }
}
