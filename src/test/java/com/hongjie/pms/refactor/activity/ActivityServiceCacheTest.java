package com.hongjie.pms.refactor.activity;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.cache.DistributedCache;
import com.hongjie.pms.common.pojo.UserInfo;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.activity.cache.ActivityCache;
import com.hongjie.pms.modules.activity.service.Impl.ActivityServiceImpl;
import com.hongjie.pms.modules.activity.mapper.*;
import com.hongjie.pms.modules.activity.entity.*;
import com.hongjie.pms.modules.activity.dto.request.*;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.modules.like.mapper.LikeRecordMapper;
import com.hongjie.pms.modules.like.entity.LikeRecord;
import com.hongjie.pms.modules.following.mapper.FollowMapper;
import com.hongjie.pms.modules.following.entity.Follow;
import com.hongjie.pms.refactor.audit.handler.ActivityAuditHandler;
import org.redisson.api.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityServiceCacheTest {
    @Mock ActivityMapper mapper;
    @Mock ActivitySignupMapper signups;
    @Mock UserMapper users;
    @Mock LikeRecordMapper likes;
    @Mock FollowMapper follows;
    @Mock DistributedCache cache;
    @Mock ActivityCache activities;
    @Mock RedissonClient redisson;
    @Mock com.hongjie.pms.refactor.message.api.MessageService messages;
    @Mock com.hongjie.pms.common.punishment.scheduler.DelayTaskService delay;
    @Mock com.hongjie.pms.modules.feed.service.FeedService feed;
    @Mock com.hongjie.pms.modules.audit.service.AuditService audit;
    @Mock org.springframework.context.ApplicationEventPublisher events;
    @InjectMocks ActivityServiceImpl service;
    @InjectMocks ActivityAuditHandler auditHandler;

    Activity activity;
    @BeforeEach void setup() {
        UserContext.setUser(new UserInfo(11L, "viewer", 0, "viewer", null));
        activity = new Activity(); activity.setId(7L); activity.setUserId(11L);
        activity.setAuditStatus(1); activity.setDeleted(0); activity.setStatus(0);
        activity.setViewCount(0); activity.setCurrentPeople(0); activity.setMaxPeople(10);
        activity.setStartTime(LocalDateTime.now().plusDays(1));
        activity.setEndTime(LocalDateTime.now().plusDays(2));
    }
    @AfterEach void cleanup() { UserContext.clear(); }

    @Test void detailRefillsPersonalStatePerViewerWithoutSharingDto() {
        when(mapper.selectById(7L)).thenReturn(activity);
        User owner = new User(); owner.setId(11L); when(users.selectById(11L)).thenReturn(owner);
        when(signups.selectOne(any())).thenReturn(new ActivitySignup()).thenReturn(null);
        when(likes.selectOne(any())).thenReturn(new LikeRecord()).thenReturn(null);
        when(follows.selectOne(any())).thenReturn(new Follow()).thenReturn(null);
        var first = service.getActivityDetail(7L);
        UserContext.setUser(new UserInfo(12L, "other", 0, null, null));
        var second = service.getActivityDetail(7L);
        assertEquals(1, first.getIsSignUp()); assertEquals(1, first.getIsLike());
        assertEquals(0, second.getIsSignUp()); assertEquals(0, second.getIsLike());
        assertEquals(Boolean.TRUE, first.getUser().getIsFollow());
        assertNotEquals(Boolean.TRUE, second.getUser().getIsFollow());
        assertEquals(1, second.getViewCount());
        verify(mapper).update(isNull(), argThat(wrapper ->
                ((com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Activity>) wrapper)
                        .getSqlSet().equals("view_count = COALESCE(view_count, 0) + 1")));
        verify(mapper, never()).updateById(any(Activity.class));
        verifyNoInteractions(activities);
        verifyNoInteractions(cache);
    }
    @Test void anonymousDetailDoesNotQueryPersonalTables() {
        UserContext.clear(); when(mapper.selectById(7L)).thenReturn(activity);
        User owner = new User(); owner.setId(11L); when(users.selectById(11L)).thenReturn(owner);
        assertEquals(0, service.getActivityDetail(7L).getIsSignUp());
        verifyNoInteractions(signups, likes, follows, cache);
    }
    @Test void missingDeletedAndUnapprovedDetailsAreRejected() {
        when(mapper.selectById(7L)).thenReturn(null, activity, activity);
        assertThrows(BusinessException.class, () -> service.getActivityDetail(7L));
        activity.setDeleted(1);
        assertThrows(BusinessException.class, () -> service.getActivityDetail(7L));
        activity.setDeleted(0); activity.setAuditStatus(0);
        assertThrows(BusinessException.class, () -> service.getActivityDetail(7L));
        verifyNoInteractions(cache);
    }
    @Test void dateFilteredListAlwaysQueriesDatabase() {
        when(mapper.selectPage(any(), any())).thenReturn(new Page<Activity>(1,10));
        var request = new ActivityListRequestDto(); request.setStartDate(LocalDateTime.now());
        service.getActivityList(request); request.setEndDate(LocalDateTime.now().plusDays(2));
        service.getActivityList(request);
        verify(mapper, times(2)).selectPage(any(), any()); verifyNoInteractions(cache);
    }
    @Test void listSignupStateIsRefilledForEachViewer() {
        var page = new Page<Activity>(1,10,1); page.setRecords(List.of(activity));
        when(mapper.selectPage(any(), any())).thenReturn(page);
        User owner = new User(); owner.setId(11L); when(users.selectBatchIds(any())).thenReturn(List.of(owner));
        ActivitySignup signup = new ActivitySignup(); signup.setActivityId(7L);
        when(signups.selectList(any())).thenReturn(List.of(signup), List.of());
        var request = new ActivityListRequestDto();
        assertEquals(1, service.getActivityList(request).getRecords().get(0).getIsSignedUp());
        UserContext.setUser(new UserInfo(12L, "other", 0, null, null));
        assertEquals(0, service.getActivityList(request).getRecords().get(0).getIsSignedUp());
        verifyNoInteractions(cache);
    }
    @Test void deleteEvicts() {
        when(mapper.selectById(7L)).thenReturn(activity); when(mapper.updateById(activity)).thenReturn(1);
        service.deleteActivity(7L); verify(activities).evictAfterCommit(7L);
    }
    @Test void failedDeleteDoesNotEvict() {
        when(mapper.selectById(7L)).thenReturn(activity);
        assertThrows(RuntimeException.class, () -> service.deleteActivity(7L)); verifyNoInteractions(activities);
    }
    @Test void restoreEvicts() {
        activity.setDeleted(1); when(mapper.selectById(7L)).thenReturn(activity);
        when(mapper.updateById(activity)).thenReturn(1);
        service.recoverActivity(7L); verify(activities).evictAfterCommit(7L);
    }
    @Test void cancelSignupEvicts() {
        when(mapper.selectById(7L)).thenReturn(activity);
        when(signups.selectOne(any())).thenReturn(new ActivitySignup());
        service.cancelSignUp(7L); verify(activities).evictAfterCommit(7L);
    }
    @Test void signupEvictsSynchronouslyAfterSuccessfulWrite() throws Exception {
        activity.setUserId(99L); when(mapper.selectById(7L)).thenReturn(activity);
        RLock lock = mock(RLock.class); when(redisson.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(1,2,TimeUnit.SECONDS)).thenReturn(true);
        when(mapper.incrementCurrentPeople(7L)).thenReturn(1);
        var request = new SignUpInfoRequest(); request.setActivityId(7L);
        service.signUp(request); verify(activities).evictAfterCommit(7L);
    }
    @Test void fallbackTtlUsesSeconds() {
        service.fallbackGetActivityDetail(7L);
        verify(cache).put(eq("activity:fallback_7"), any(), eq(60L), eq(TimeUnit.SECONDS));
    }
    @Test void auditClearUsesCentralInvalidation() {
        auditHandler.clearCache(7L); verify(activities).evictAfterCommit(7L);
    }
    ActivityRequestDto request() {
        var request = new ActivityRequestDto(); request.setId(7L);
        request.setStartTime(activity.getStartTime()); request.setEndTime(activity.getEndTime());
        request.setMaxPeople(10); return request;
    }
    @Test void createEvicts() {
        when(mapper.insert(any(Activity.class))).thenAnswer(call -> {
            ((Activity) call.getArgument(0)).setId(7L); return 1;
        });
        User owner = new User(); owner.setId(11L); when(users.selectById(11L)).thenReturn(owner);
        service.postActivity(request()); verify(activities).evictAfterCommit(7L);
    }
    @Test void editEvicts() {
        when(mapper.selectOne(any())).thenReturn(activity);
        when(mapper.update(eq(activity), any())).thenReturn(1);
        service.updateActivity(request()); verify(activities).evictAfterCommit(7L);
    }
    @Test void failedEditDoesNotEvict() {
        when(mapper.selectOne(any())).thenReturn(activity);
        assertThrows(RuntimeException.class, () -> service.updateActivity(request()));
        verifyNoInteractions(activities);
    }
    @Test void auditStatusWriteEvictsWithoutSeparateClearCall() {
        when(mapper.selectById(7L)).thenReturn(activity);
        auditHandler.updateAuditStatus(7L, 1, null);
        verify(activities).evictAfterCommit(7L);
    }
}
