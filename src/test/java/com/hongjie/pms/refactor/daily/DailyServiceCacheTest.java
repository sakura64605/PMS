package com.hongjie.pms.refactor.daily;

import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.modules.daily.entity.DailyPost;
import com.hongjie.pms.modules.daily.mapper.DailyPostMapper;
import com.hongjie.pms.modules.daily.service.*;
import com.hongjie.pms.modules.like.mapper.LikeRecordMapper;
import com.hongjie.pms.modules.like.entity.LikeRecord;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class DailyServiceCacheTest {
    @Test void deleteInvalidatesAfterSuccessfulWriteButNotForUnauthorizedUser() {
        var posts = mock(DailyPostMapper.class);
        var cache = mock(DailyCacheInvalidator.class);
        var service = mock(DailyPostService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "dailyPostMapper", posts);
        ReflectionTestUtils.setField(service, "cacheInvalidator", cache);
        var post = new DailyPost(); post.setId(3L); post.setUserId(12L);
        when(posts.selectById(3L)).thenReturn(post);
        try (var user = mockStatic(UserContext.class)) {
            user.when(UserContext::getUserId).thenReturn(99L);
            assertThatThrownBy(() -> service.delete(3L)).isInstanceOf(RuntimeException.class);
            verifyNoInteractions(cache);
            user.when(UserContext::getUserId).thenReturn(12L);
            service.delete(3L);
            var order = inOrder(posts, cache);
            order.verify(posts).updateById(post);
            order.verify(cache).allAfterCommit();
            assertThat(post.getStatus()).isZero();
        }
    }

    @Test void bothLikeTransitionsScheduleInvalidation() {
        var service = mock(DailyPostService.class, CALLS_REAL_METHODS);
        var posts = mock(DailyPostMapper.class);
        var likes = mock(LikeRecordMapper.class);
        var cache = mock(DailyCacheInvalidator.class);
        ReflectionTestUtils.setField(service, "dailyPostMapper", posts);
        ReflectionTestUtils.setField(service, "likeRecordMapper", likes);
        ReflectionTestUtils.setField(service, "cacheInvalidator", cache);
        ReflectionTestUtils.setField(service, "eventPublisher", mock(org.springframework.context.ApplicationEventPublisher.class));
        assertThat(service.like(3L)).isTrue();
        when(likes.selectOne(any())).thenReturn(new LikeRecord());
        assertThat(service.like(3L)).isFalse();
        verify(cache, times(2)).allAfterCommit();
        verify(posts).incrementLikeCount(3L);
        verify(posts).decrementLikeCount(3L);
    }

    @Test void behaviorUpdatesInvalidateViewerPagesOnlyAfterSuccessfulUpdate() {
        var service = mock(DailyRecommendService.class, CALLS_REAL_METHODS);
        var interest = mock(DailyUserInterestService.class);
        var cache = mock(DailyCacheInvalidator.class);
        ReflectionTestUtils.setField(service, "interestService", interest);
        ReflectionTestUtils.setField(service, "cacheInvalidator", cache);
        service.recordAction(12L, 3L, "like");
        var order = inOrder(interest, cache);
        order.verify(interest).updateInterest(12L, 3L, "like");
        order.verify(cache).viewerAfterCommit(12L);
        clearInvocations(cache);
        doThrow(new IllegalStateException("failed write")).when(interest).updateInterest(12L, 3L, "view");
        assertThatThrownBy(() -> service.recordAction(12L, 3L, "view")).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(cache);
    }
}
