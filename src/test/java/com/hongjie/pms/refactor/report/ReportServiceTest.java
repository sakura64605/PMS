package com.hongjie.pms.refactor.report;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.pojo.UserInfo;
import com.hongjie.pms.modules.report.dto.ReportRequest;
import com.hongjie.pms.modules.report.entity.ReportRecord;
import com.hongjie.pms.modules.report.mapper.ReportRecordMapper;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.refactor.core.enums.BizTargetType;
import com.hongjie.pms.refactor.extension.target.TargetTypeCodec;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportServiceTest {
    ReportRecordMapper records;UserMapper users;ReportWriteMapper locks;ApplicationEventPublisher events;
    ReportTarget target;PmsReportServiceImpl service;
    @BeforeAll static void metadata(){TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"report-test"),ReportRecord.class);}
    @BeforeEach void setup(){
        UserContext.setUser(new UserInfo(1L,"admin",1,null,null));
        records=mock(ReportRecordMapper.class);users=mock(UserMapper.class);locks=mock(ReportWriteMapper.class);events=mock(ApplicationEventPublisher.class);
        target=mock(ReportTarget.class);when(target.type()).thenReturn(BizTargetType.PET_POST);when(target.description()).thenReturn("宠物");when(target.lock(10L)).thenReturn(true);
        when(locks.pending(anyLong(),anyLong(),anyList())).thenReturn(List.of());
        service=new PmsReportServiceImpl(records,users,locks,events,new TargetTypeCodec(),List.of(target));
        clearInvocations(target);
        when(records.update(isNull(),any())).thenReturn(1);
    }
    @AfterEach void clear(){UserContext.clear();}
    ReportRequest request(){var r=new ReportRequest();r.setTargetId(10L);r.setTargetType("pet_post");r.setReason("reason");return r;}
    ReportRecord record(){var r=new ReportRecord();r.setId(100L);r.setTargetId(10L);r.setTargetType("pet");r.setStatus(0);r.setReporterId(2L);return r;}
    @Test void submitNormalizesTypeAndLocksBeforeCheckingDuplicates(){
        service.submit(1L,request());
        var order=inOrder(target,locks,records);order.verify(target).lock(10L);order.verify(locks).pending(1L,10L,BizTargetType.PET_POST.getAliases());
        var saved=ArgumentCaptor.forClass(ReportRecord.class);order.verify(records).insert(saved.capture());assertThat(saved.getValue().getTargetType()).isEqualTo("pet");
        verifyNoInteractions(events);
    }
    @Test void duplicateSubmitNeverWrites(){
        when(locks.pending(anyLong(),anyLong(),anyList())).thenReturn(List.of(100L));
        assertThatThrownBy(()->service.submit(1L,request())).isInstanceOf(BusinessException.class);verifyNoInteractions(records,events);
    }
    @Test void missingTargetCannotBeReported(){
        when(target.lock(10L)).thenReturn(false);
        assertThatThrownBy(()->service.submit(1L,request())).isInstanceOf(BusinessException.class);verifyNoInteractions(records,locks,events);
    }
    @Test void spoofedReporterIsRejected(){
        assertThatThrownBy(()->service.submit(2L,request())).isInstanceOf(BusinessException.class);verifyNoInteractions(records,locks,target);
    }
    @Test void anonymousCannotSubmit(){UserContext.clear();assertThatThrownBy(()->service.submit(null,request())).isInstanceOf(BusinessException.class);}
    @Test void overlongReasonFailsBeforeWriting(){var r=request();r.setReason("x".repeat(201));assertThatThrownBy(()->service.submit(1L,r)).isInstanceOf(BusinessException.class);verifyNoInteractions(records,locks);}
    @Test void acceptedReportUsesConditionalTransitionBeforeDisposingTarget(){
        when(records.selectById(100L)).thenReturn(record());service.handle(100L,1L,1,"done");
        var order=inOrder(target,records,events);order.verify(records).selectById(100L);order.verify(target).lock(10L);
        order.verify(records).update(isNull(),any());order.verify(target).accept(10L);order.verify(events).publishEvent(any(ReportTargetChanged.class));
    }
    @Test void rejectedReportNeverTouchesTarget(){when(records.selectById(100L)).thenReturn(record());service.handle(100L,1L,2,"rejected");verifyNoInteractions(target,events);}
    @Test void invalidStatusCannotReturnReportToPending(){assertThatThrownBy(()->service.handle(100L,1L,0,"bad")).isInstanceOf(BusinessException.class);verifyNoInteractions(records,events);}
    @Test void lostConditionalUpdateCannotRepeatPunishment(){
        when(records.selectById(100L)).thenReturn(record());when(records.update(isNull(),any())).thenReturn(0);
        assertThatThrownBy(()->service.handle(100L,1L,1,"done")).isInstanceOf(BusinessException.class);
        verify(target,never()).accept(anyLong());verifyNoInteractions(events);
    }
    @Test void targetFailurePropagatesAndNeverPublishes(){
        when(records.selectById(100L)).thenReturn(record());doThrow(new IllegalStateException("db failure")).when(target).accept(10L);
        assertThatThrownBy(()->service.handle(100L,1L,1,"done")).isInstanceOf(IllegalStateException.class);verifyNoInteractions(events);
    }
    @Test void ordinaryUserCannotReadAdminList(){UserContext.setUser(new UserInfo(2L,"user",0,null,null));assertThatThrownBy(()->service.getList(null,null,1,10)).isInstanceOf(BusinessException.class);verifyNoInteractions(records,users);}
    @Test void listBatchesReportersHandlersAndTargetTitles(){
        var one=record();one.setHandlerId(1L);var two=record();two.setId(101L);two.setTargetId(11L);
        when(records.selectPage(any(Page.class),any())).thenReturn(new Page<ReportRecord>(1,10,2).setRecords(List.of(one,two)));
        var user=new User();user.setId(2L);user.setUserName("reporter");when(users.selectBatchIds(any())).thenReturn(List.of(user));
        when(target.titles(any())).thenReturn(Map.of(10L,"title one",11L,"title two"));
        var page=service.getList(null,"pet",1,10);assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords().get(0).getReporterName()).isEqualTo("reporter");
        assertThat(page.getRecords().get(1).getTargetTitle()).isEqualTo("title two");
        verify(users).selectBatchIds(argThat(ids->ids.size()==2&&ids.containsAll(List.of(1L,2L))));
        verify(target).titles(Set.of(10L,11L));verify(users,never()).selectById(any());
    }
}
