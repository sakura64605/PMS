package com.hongjie.pms.refactor.like;

import com.hongjie.pms.common.utils.RedisScanUtil;
import com.hongjie.pms.modules.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class LikeChangedListener {
    private final MessageService messages;
    private final RedisTemplate<String, Object> redis;
    private final RedisScanUtil scan;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChanged(LikeChanged event) {
        if ("pet_activity".equals(event.targetType())) {
            redis.delete("activity:" + event.targetId());
            scan.scanAndDelete("activityList:*");
            redis.delete("userActivity:" + event.actorId());
        }
        if (event.liked() && !Objects.equals(event.actorId(), event.target().ownerId())) {
            messages.sendLikeNotification(event.target().ownerId(), event.actorId(), event.targetType(),
                    event.target().title(), event.targetId(), event.target().link());
        }
    }
}
