package com.hongjie.pms.refactor.following;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.pojo.UserInfo;
import com.hongjie.pms.modules.following.entity.Follow;
import com.hongjie.pms.modules.following.mapper.FollowMapper;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FollowServiceTest {
    com.hongjie.pms.common.cache.DistributedCache cache;
    FollowMapper follows;UserMapper users;FollowWriteMapper writes;FollowInboxWriter inbox;ApplicationEventPublisher events;PmsFollowServiceImpl service;
    @BeforeAll static void metadata(){TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"follow-test"),Follow.class);}
    static User user(long id){var u=new User();u.setId(id);u.setUserName("user"+id);u.setStatus(1);return u;}
    @BeforeEach void setup(){
        UserContext.setUser(new UserInfo(2L,"viewer",0,null,null));follows=mock(FollowMapper.class);users=mock(UserMapper.class);writes=mock(FollowWriteMapper.class);inbox=mock(FollowInboxWriter.class);events=mock(ApplicationEventPublisher.class);
        cache=mock(com.hongjie.pms.common.cache.DistributedCache.class);
        service=new PmsFollowServiceImpl(cache,follows,users,writes,inbox,events);
        when(writes.lockUser(1L)).thenReturn(user(1));when(writes.lockUser(2L)).thenReturn(user(2));when(users.selectById(1L)).thenReturn(user(1));
        when(writes.relation(anyLong(),anyLong())).thenReturn(null);
        when(writes.insert(anyLong(),anyLong())).thenReturn(1);when(writes.delete(anyLong(),anyLong(),anyLong())).thenReturn(1);
        when(writes.followingCount(anyLong(),anyInt())).thenReturn(1);when(writes.followerCount(anyLong(),anyInt())).thenReturn(1);
    }
    @AfterEach void clear(){UserContext.clear();}
    @Test void followLocksAscendingAndUsesOutgoingDirection(){
        service.followUser(2L,1L);var order=inOrder(writes,inbox,events);
        order.verify(writes).lockUser(1L);order.verify(writes).lockUser(2L);order.verify(writes).relation(2L,1L);order.verify(writes).insert(2L,1L);
        order.verify(writes).followingCount(2L,1);order.verify(writes).followerCount(1L,1);order.verify(inbox).populate(eq(2L),any());order.verify(events).publishEvent(new FollowCreated(2L,1L,"user2"));
    }
    @Test void unfollowDoesNotDeleteReverseEdgeOrNotify(){
        when(writes.relation(2L,1L)).thenReturn(99L);service.followUser(2L,1L);
        verify(writes).delete(99L,2L,1L);verify(writes).followingCount(2L,-1);verify(writes).followerCount(1L,-1);verify(inbox).remove(2L,1L);verifyNoInteractions(events);
    }
    @Test void selfFollowRejected(){assertThatThrownBy(()->service.followUser(2L,2L)).isInstanceOf(BusinessException.class);verifyNoInteractions(writes,inbox,events);}
    @Test void spoofedActorRejected(){assertThatThrownBy(()->service.followUser(1L,2L)).isInstanceOf(BusinessException.class);verifyNoInteractions(writes);}
    @Test void anonymousRejected(){UserContext.clear();assertThatThrownBy(()->service.followUser(2L,1L)).isInstanceOf(BusinessException.class);verifyNoInteractions(writes);}
    @Test void missingTargetRejected(){when(writes.lockUser(1L)).thenReturn(null);assertThatThrownBy(()->service.followUser(2L,1L)).isInstanceOf(BusinessException.class);verify(writes,never()).insert(anyLong(),anyLong());}
    @Test void disabledTargetCannotBeFollowed(){var u=user(1);u.setStatus(0);when(writes.lockUser(1L)).thenReturn(u);assertThatThrownBy(()->service.followUser(2L,1L)).isInstanceOf(BusinessException.class);verifyNoInteractions(inbox,events);}
    @Test void disabledTargetCanStillBeUnfollowed(){var u=user(1);u.setStatus(0);when(writes.lockUser(1L)).thenReturn(u);when(writes.relation(2L,1L)).thenReturn(99L);service.followUser(2L,1L);verify(inbox).remove(2L,1L);}
    @Test void inboxFailureCannotPublishNotification(){doThrow(new IllegalStateException("inbox failure")).when(inbox).populate(anyLong(),any());assertThatThrownBy(()->service.followUser(2L,1L)).isInstanceOf(IllegalStateException.class);verifyNoInteractions(events);}
    @Test void followerListPreservesTotalAndComputesActualReciprocity(){
        var a=new Follow();a.setFollowerId(1L);var b=new Follow();b.setFollowerId(3L);
        when(follows.selectPage(any(Page.class),any())).thenReturn(new Page<Follow>(2,2,7).setRecords(List.of(a,b)));
        when(users.selectBatchIds(any())).thenReturn(List.of(user(1),user(3)));when(follows.selectFollowedIds(2L,List.of(1L,3L))).thenReturn(List.of(3L));
        var result=service.listFollower(2L,2,2);assertThat(result.getTotal()).isEqualTo(7);assertThat(result.getCurrent()).isEqualTo(2);
        assertThat(result.getRecords()).extracting(u->u.getIsFollow()).containsExactly(false,true);
    }
    @Test void emptyPageKeepsMetadataAndAvoidsEmptyInQuery(){
        when(follows.selectPage(any(Page.class),any())).thenReturn(new Page<Follow>(5,10,3).setRecords(List.of()));
        var result=service.listFollowing(2L,5,10);assertThat(result.getTotal()).isEqualTo(3);assertThat(result.getCurrent()).isEqualTo(5);assertThat(result.getRecords()).isEmpty();verifyNoInteractions(users);
    }
    @Test void bothBranchesEvictAfterCommit() {
        for (Long relation : new Long[]{null,99L}) {
            reset(cache); when(writes.relation(2L,1L)).thenReturn(relation);
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
            org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
            try {
                service.followUser(2L,1L); verifyNoInteractions(cache);
                org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations().forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
                verify(cache).delete("user_2"); verify(cache).delete("user_1");
            } finally { org.springframework.transaction.support.TransactionSynchronizationManager.clear(); }
        }
    }
    @Test void rollbackDoesNotEvictEitherProfile() {
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.followUser(2L,1L);
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
            verifyNoInteractions(cache);
        } finally { org.springframework.transaction.support.TransactionSynchronizationManager.clear(); }
    }
}
