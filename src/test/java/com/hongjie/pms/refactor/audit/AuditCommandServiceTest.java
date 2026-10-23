package com.hongjie.pms.refactor.audit;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.UserInfo;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.audit.entity.AuditRecord;
import com.hongjie.pms.modules.audit.mapper.AuditRecordMapper;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditCommandServiceTest {
    AuditWriteTarget target;AuditWriteMapper locks;AuditRecordMapper records;ApplicationEventPublisher events;AuditCommandService service;
    @BeforeAll static void metadata(){TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"test"),AuditRecord.class);}
    static AuditRecord record(){var row=new AuditRecord();row.setId(10L);return row;}
    @BeforeEach void setup(){
        UserContext.setUser(new UserInfo(1L,"admin",1,null,null));
        target=mock(AuditWriteTarget.class);when(target.type()).thenReturn("adopt");
        when(target.load(anyLong())).thenReturn(new AuditWriteTarget.Snapshot(2L,"title","pet_post"));
        locks=mock(AuditWriteMapper.class);records=mock(AuditRecordMapper.class);events=mock(ApplicationEventPublisher.class);
        when(locks.pending(anyString(),anyLong())).thenReturn(List.of(record()));
        when(records.update(isNull(),any())).thenReturn(1);
        service=new AuditCommandService(List.of(target),locks,records,events);
    }
    @AfterEach void clear(){UserContext.clear();}
    @Test void approvedOnlyAfterCurrentReadAndConditionalRecordUpdate(){
        service.approve(1L,"adopt");
        var order=inOrder(target,locks,records,events);
        order.verify(target).lock(1L);order.verify(locks).pending("adopt",1L);
        order.verify(target).load(1L);order.verify(records).update(isNull(),any());
        order.verify(target).decide(1L,1);order.verify(events).publishEvent(any(AuditChanged.class));
    }
    @Test void alreadyHandledRecordCannotEmitAnotherEvent(){
        when(locks.pending("adopt",1L)).thenReturn(List.of());
        assertThatThrownBy(()->service.approve(1L,"adopt")).isInstanceOf(BusinessException.class);
        verify(target,never()).decide(anyLong(),anyInt());verifyNoInteractions(records,events);
    }
    @Test void historicalDuplicatePendingRowsAreRejected(){
        when(locks.pending("adopt",1L)).thenReturn(List.of(record(),record()));
        assertThatThrownBy(()->service.approve(1L,"adopt")).isInstanceOf(BusinessException.class);
        verifyNoInteractions(records,events);
    }
    @Test void lostConditionalUpdateNeverChangesTarget(){
        when(records.update(isNull(),any())).thenReturn(0);
        assertThatThrownBy(()->service.approve(1L,"adopt")).isInstanceOf(BusinessException.class);
        verify(target,never()).decide(anyLong(),anyInt());verifyNoInteractions(events);
    }
    @Test void batchDeduplicatesAndLocksInAscendingOrder(){
        service.batchApprove(List.of(3L,1L,3L),"adopt");
        var order=inOrder(target);order.verify(target).lock(1L);order.verify(target).decide(1L,1);
        order.verify(target).lock(3L);order.verify(target).decide(3L,1);
        verify(events,times(2)).publishEvent(any(AuditChanged.class));
    }
    @Test void ordinaryUserCannotDecide(){
        UserContext.setUser(new UserInfo(2L,"user",0,null,null));
        assertThatThrownBy(()->service.approve(1L,"adopt")).isInstanceOf(BusinessException.class);
        verifyNoInteractions(locks,records,events);
    }
    @Test void blankRejectionReasonFailsBeforeDatabaseWork(){
        assertThatThrownBy(()->service.reject(1L,"adopt"," ")).isInstanceOf(BusinessException.class);
        verifyNoInteractions(locks,records,events);
    }
    @Test void submitLocksBeforeCheckingAndInserting(){
        when(locks.pending("adopt",1L)).thenReturn(List.of());
        service.submit("adopt",1L);
        var order=inOrder(target,locks,records,events);
        order.verify(target).lock(1L);order.verify(locks).pending("adopt",1L);
        order.verify(records).insert(any(AuditRecord.class));order.verify(events).publishEvent(any(AuditChanged.class));
    }
    @Test void duplicateSubmitNeverInserts(){
        assertThatThrownBy(()->service.submit("adopt",1L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(records,events);
    }
    @Test void emptyBatchIsRejected(){
        assertThatThrownBy(()->service.batchApprove(List.of(),"adopt")).isInstanceOf(BusinessException.class);
        verifyNoInteractions(locks,records,events);
    }
}
