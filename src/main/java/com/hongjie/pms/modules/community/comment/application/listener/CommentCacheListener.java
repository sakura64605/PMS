package com.hongjie.pms.modules.community.comment.application.listener;

import com.hongjie.pms.platform.cache.DistributedCache;
import com.hongjie.pms.platform.utils.RedisScanUtil;
import com.hongjie.pms.modules.community.comment.api.event.CommentCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class CommentCacheListener {
    private final DistributedCache cache;
    private final RedisScanUtil scan;
    @TransactionalEventListener
    public void created(CommentCreatedEvent event) {
        switch (event.getTargetType()) {
            case PET_POST -> cache.delete("pet_" + event.getTargetId());
            case PET_ACTIVITY -> {
                cache.delete("activity_" + event.getTargetId());
                cache.delete("activity:fallback_" + event.getTargetId());
                scan.scanAndDelete("activityList_*");
            }
            default -> { /* Daily snapshots are invalidated by their own module. */ }
        }
    }
}
