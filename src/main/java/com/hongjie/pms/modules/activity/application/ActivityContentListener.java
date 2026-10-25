package com.hongjie.pms.modules.activity.application;

import com.hongjie.pms.modules.search.api.event.ActivityUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityContentListener {
    private final ApplicationEventPublisher events;

    @TransactionalEventListener
    public void changed(ActivityContentChanged event) {
        try {
            events.publishEvent(new ActivityUpdatedEvent(this, event.activityId(), "content"));
        } catch (RuntimeException failure) {
            log.error("活动已提交，搜索同步事件发布失败: activityId={}", event.activityId(), failure);
        }
    }
}
