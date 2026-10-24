package com.hongjie.pms.refactor.like;

import com.hongjie.pms.common.utils.RedisScanUtil;
import com.hongjie.pms.modules.message.service.MessageService;
import org.springframework.data.redis.core.RedisTemplate;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class LikeChangedListenerTest {
    MessageService messages = mock(MessageService.class);
    RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    RedisScanUtil scan = mock(RedisScanUtil.class);
    LikeChangedListener listener = new LikeChangedListener(messages, redis, scan);
    @Test void sendsNotificationWithLegacyFields() {
        listener.onChanged(new LikeChanged(1L, 10L, "pet_post",
                new LikeTarget.Snapshot(2L, "title", "/pet/10", 0), true));
        verify(messages).sendLikeNotification(2L, 1L, "pet_post", "title", 10L, "/pet/10");
    }
    @Test void petUnlikeEvictsTheActualDetailKey() {
        listener.onChanged(new LikeChanged(1L, 10L, "pet_post",
                new LikeTarget.Snapshot(2L, "title", "/pet/10", 1), false));
        verify(redis).delete("pet_10");
        verifyNoInteractions(messages);
    }
    @Test void selfLikeAndUnlikeNeverNotify() {
        var self = new LikeTarget.Snapshot(1L, "title", "/pet/10", 0);
        listener.onChanged(new LikeChanged(1L, 10L, "pet_post", self, true));
        listener.onChanged(new LikeChanged(2L, 10L, "pet_post", self, false));
        verifyNoInteractions(messages);
    }
    @Test void activityUnlikeStillEvictsAllExistingCacheRegions() {
        listener.onChanged(new LikeChanged(1L, 10L, "pet_activity",
                new LikeTarget.Snapshot(2L, "title", "/activity/10", 1), false));
        verify(redis).delete("activity_10");
        verify(scan).scanAndDelete("userActivity_*");
        verify(scan).scanAndDelete("activityList_*");
        verifyNoInteractions(messages);
    }
}
