package com.hongjie.pms.modules.community.like.application;

import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.pojo.UserInfo;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.community.like.api.dto.request.LikeRequest;
import com.hongjie.pms.modules.community.like.infrastructure.persistence.entity.LikeRecord;
import com.hongjie.pms.modules.community.like.infrastructure.persistence.mapper.LikeRecordMapper;
import com.hongjie.pms.shared.core.strategy.StrategyRegistry;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class PmsLikeServiceTest {
    LikeTarget target;
    LikeRecordMapper records;
    ApplicationEventPublisher events;
    PmsLikeServiceImpl service;
    LikeRequest request;
    @BeforeEach void setup() {
        UserContext.setUser(new UserInfo(1L, "tester", 0, null, null));
        target = mock(LikeTarget.class);
        when(target.type()).thenReturn("pet_post");
        when(target.load(10L)).thenReturn(new LikeTarget.Snapshot(2L, "title", "/pet/10", 3));
        records = mock(LikeRecordMapper.class);
        events = mock(ApplicationEventPublisher.class);
        service = new PmsLikeServiceImpl(new StrategyRegistry<>("test", List.of(target), t -> List.of(t.type())), records, events);
        request = new LikeRequest(); request.setTargetId(10L); request.setTargetType("pet_post");
    }
    @AfterEach void clear() { UserContext.clear(); }
    @Test void createsRecordBeforeIncrementAndPublishesEvent() {
        var result = service.like(request);
        assertThat(result.getIsLiked()).isTrue();
        assertThat(result.getLikeCount()).isEqualTo(4);
        var order = inOrder(records, target, events);
        order.verify(target).load(10L);
        order.verify(records).selectOne(any());
        order.verify(records).insert(any(LikeRecord.class));
        order.verify(target).increment(10L);
        order.verify(events).publishEvent(any(LikeChanged.class));
    }
    @Test void removesExistingRecordAndFloorsCountAtZero() {
        when(records.selectOne(any())).thenReturn(LikeRecord.builder().id(50L).build());
        when(records.deleteById(50L)).thenReturn(1);
        when(target.load(10L)).thenReturn(new LikeTarget.Snapshot(2L, "title", "/pet/10", 0));
        var result = service.like(request);
        assertThat(result.getIsLiked()).isFalse();
        assertThat(result.getLikeCount()).isZero();
        verify(target).decrement(10L);
    }
    @Test void failedRecordInsertNeverChangesCounterOrEmitsEvent() {
        when(records.insert(any(LikeRecord.class))).thenThrow(new IllegalStateException("duplicate"));
        assertThatThrownBy(() -> service.like(request)).isInstanceOf(IllegalStateException.class);
        verify(target, never()).increment(any()); verifyNoInteractions(events);
    }
    @Test void concurrentRemovalDoesNotDecrementTwice() {
        when(records.selectOne(any())).thenReturn(LikeRecord.builder().id(50L).build());
        assertThatThrownBy(() -> service.like(request)).isInstanceOf(BusinessException.class);
        verify(target, never()).decrement(any()); verifyNoInteractions(events);
    }
    @Test void unknownTypeFailsBeforeAnyDatabaseWrite() {
        request.setTargetType("unsupported");
        assertThatThrownBy(() -> service.like(request)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(records, events);
    }
    @Test void anonymousCannotLike() {
        UserContext.clear();
        assertThatThrownBy(() -> service.like(request)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(records, events);
    }
}
