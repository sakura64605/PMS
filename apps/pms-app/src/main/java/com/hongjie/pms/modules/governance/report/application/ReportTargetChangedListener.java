package com.hongjie.pms.modules.governance.report.application;

import com.hongjie.pms.platform.cache.DistributedCache;
import com.hongjie.pms.platform.cache.toolkit.CacheUtil;
import com.hongjie.pms.platform.utils.RedisScanUtil;
import com.hongjie.pms.modules.search.api.event.*;
import org.springframework.beans.factory.annotation.Qualifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class ReportTargetChangedListener {
    private final DistributedCache cache;
    private final RedisScanUtil scan;
    private final ApplicationEventPublisher events;

    public ReportTargetChangedListener(
            @Qualifier("distributedCache") DistributedCache cache,
            RedisScanUtil scan,
            ApplicationEventPublisher events) {
        this.cache = cache;
        this.scan = scan;
        this.events = events;
    }

    @TransactionalEventListener
    public void changed(ReportTargetChanged event) {
        switch (event.type()) {
            case PET_POST -> {
                run(
                        event,
                        () -> cache.delete(CacheUtil.buildKey("pet", event.targetId().toString())));
                run(event, () -> scan.scanAndDelete("pet:" + event.targetId()));
                run(event, () -> scan.scanAndDelete("petList_*"));
                run(
                        event,
                        () ->
                                events.publishEvent(
                                        new PetPostUpdatedEvent(
                                                this, event.targetId(), "content")));
            }
            case PET_ACTIVITY -> {
                run(
                        event,
                        () ->
                                cache.delete(
                                        CacheUtil.buildKey(
                                                "activity", event.targetId().toString())));
                run(event, () -> scan.scanAndDelete("activity:" + event.targetId()));
                run(event, () -> scan.scanAndDelete("activityList*"));
                run(event, () -> scan.scanAndDelete("userActivity_*"));
                run(
                        event,
                        () ->
                                events.publishEvent(
                                        new ActivityUpdatedEvent(
                                                this, event.targetId(), "content")));
            }
            default -> {
                /* 评论不在搜索索引中；用户账号治理沿用既有鉴权机制。 */
            }
        }
    }

    private void run(ReportTargetChanged event, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            log.error("举报处置已提交，但缓存/搜索副作用失败: type={}, id={}", event.type(), event.targetId(), e);
        }
    }
}
