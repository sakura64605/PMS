package com.hongjie.pms.modules.search.application.index;

import com.hongjie.pms.modules.community.like.application.LikeChanged;
import com.hongjie.pms.modules.community.comment.api.event.CommentCreatedEvent;
import com.hongjie.pms.modules.activity.application.ActivityParticipationChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SearchIndexDomainListener {
    private final SearchSyncDispatch dispatch;

    @EventListener
    public void liked(LikeChanged event) {
        submit(event.targetType(), event.targetId());
    }

    @EventListener
    public void commented(CommentCreatedEvent event) {
        if (event.getTargetType() != null)
            submit(event.getTargetType().getCode(), event.getTargetId());
    }

    @EventListener
    public void participated(ActivityParticipationChanged event) {
        dispatch.submit("activity", event.activityId());
    }

    private void submit(String type, Long id) {
        if ("pet_post".equals(type)) dispatch.submit("pet", id);
        else if ("pet_activity".equals(type)) dispatch.submit("activity", id);
        else if ("daily".equals(type)) dispatch.submit("daily", id);
    }
}
