package com.hongjie.pms.modules.search.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class DailyPostUpdatedEvent extends ApplicationEvent {
    
    private final Long dailyId;
    private final String updateType;  // like, comment, view, content, audit
    
    public DailyPostUpdatedEvent(Object source, Long dailyId, String updateType) {
        super(source);
        this.dailyId = dailyId;
        this.updateType = updateType;
    }
    
    public DailyPostUpdatedEvent(Object source, Long dailyId) {
        this(source, dailyId, "content");
    }
}