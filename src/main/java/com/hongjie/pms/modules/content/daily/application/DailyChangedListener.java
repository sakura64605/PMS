package com.hongjie.pms.modules.content.daily.application;
import com.hongjie.pms.modules.content.daily.api.service.DailyCacheInvalidator;
import com.hongjie.pms.modules.search.api.event.DailyPostUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component @RequiredArgsConstructor @Slf4j
public class DailyChangedListener {
    private final DailyCacheInvalidator cache;
    private final ApplicationEventPublisher events;
    @TransactionalEventListener public void changed(DailyChanged event) {
        cache.evictAll();
        try { events.publishEvent(new DailyPostUpdatedEvent(this,event.id(),"content")); }
        catch(RuntimeException failure) { log.error("日常已提交但搜索同步事件失败: id={}",event.id(),failure); }
    }
}
