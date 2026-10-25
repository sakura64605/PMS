package com.hongjie.pms.modules.search.api.event;

import com.hongjie.pms.modules.content.daily.infrastructure.persistence.entity.DailyPost;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

@Getter
public class DailyPostPublishedEvent extends ApplicationEvent {
    private final DailyPost dailyPost;
    private final List<Long> topicIds;
    
    public DailyPostPublishedEvent(Object source, DailyPost dailyPost, List<Long> topicIds) {
        super(source);
        this.dailyPost = dailyPost;
        this.topicIds = topicIds;
    }
}