package com.hongjie.pms.modules.search.api.event;

import com.hongjie.pms.modules.activity.infrastructure.persistence.entity.Activity;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ActivityPublishedEvent extends ApplicationEvent {
    private final Activity activity;
    
    public ActivityPublishedEvent(Object source, Activity activity) {
        super(source);
        this.activity = activity;
    }
}