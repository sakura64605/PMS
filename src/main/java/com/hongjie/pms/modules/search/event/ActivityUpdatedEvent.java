package com.hongjie.pms.modules.search.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ActivityUpdatedEvent extends ApplicationEvent {
    
    private final Long activityId;
    private final String updateType;  // like, comment, view, content, audit, signup
    
    public ActivityUpdatedEvent(Object source, Long activityId, String updateType) {
        super(source);
        this.activityId = activityId;
        this.updateType = updateType;
    }
}