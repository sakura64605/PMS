package com.hongjie.pms.refactor.audit;

import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.audit.dto.AuditListDto;
import com.hongjie.pms.modules.audit.entity.AuditRecord;
import com.hongjie.pms.modules.audit.mapper.AuditRecordMapper;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditQueryServiceTest {
    AuditQuerySource source;
    AuditRecordMapper records;
    UserMapper users;
    AuditQueryService queries;
    @BeforeEach void setup() {
        source = mock(AuditQuerySource.class); when(source.type()).thenReturn("adopt");
        records = mock(AuditRecordMapper.class); users = mock(UserMapper.class);
        queries = new AuditQueryService(List.of(source), records, users);
        when(users.selectBatchIds(any())).thenReturn(List.of());
    }
    AuditQuerySource.Item item(long id, String title) {
        return new AuditQuerySource.Item(10L, AuditListDto.builder().id(id).targetType("adopt")
                .targetTypeDesc("领养").title(title).createTime(LocalDateTime.of(2026, 1, 1, 0, 0)).build());
    }
    AuditRecord record(long id, long target) {
        var r = new AuditRecord();r.setId(id);r.setTargetId(target);r.setTargetType("adopt");r.setAuditStatus(1);r.setAuditorId(20L);return r;
    }
    @Test void outOfRangeReturnsEmptyPageWithCorrectTotal() {
        when(source.pending(null)).thenReturn(List.of(item(1,"one")));
        var page = queries.pending("adopt",null,null,Integer.MAX_VALUE,100);
        assertThat(page.getRecords()).isEmpty();assertThat(page.getTotal()).isEqualTo(1);
        verifyNoInteractions(users);
    }
    @Test void batchesAuthorsOnlyForCurrentPageAndSortsTiesDeterministically() {
        when(source.pending(null)).thenReturn(List.of(item(1,"one"),item(2,"two")));
        var user = new User(); user.setId(10L);user.setUserName("author");
        when(users.selectBatchIds(any())).thenReturn(List.of(user));
        var page = queries.pending(null,null,null,1,1);
        assertThat(page.getRecords()).extracting(AuditListDto::getId).containsExactly(2L);
        assertThat(page.getRecords().get(0).getUser().getUsername()).isEqualTo("author");
        verify(users).selectBatchIds(List.of(10L));verify(users,never()).selectById(any());
    }
    @Test void historyFiltersBeforePaginationAndCountsOnlyMatches() {
        when(records.selectList(any())).thenReturn(List.of(record(3,1),record(2,2),record(1,3)));
        when(source.findAll(any())).thenReturn(Map.of(1L,item(1,"other"),2L,item(2,"wanted"),3L,item(3,"wanted too")));
        var page = queries.history("adopt","wanted",1,1,1);
        assertThat(page.getTotal()).isEqualTo(2);assertThat(page.getRecords().get(0).getTargetId()).isEqualTo(2);
        verify(source).findAll(List.of(1L,2L,3L));
        verify(users).selectBatchIds(argThat(ids -> ids.size()==2 && ids.containsAll(List.of(10L,20L))));
    }
    @Test void missingTargetsDoNotCorruptHistoryTotal() {
        when(records.selectList(any())).thenReturn(List.of(record(1,999)));
        when(source.findAll(any())).thenReturn(Map.of());
        assertThat(queries.history(null,null,null,1,10).getTotal()).isZero();
        verifyNoInteractions(users);
    }
    @Test void missingAuthorAndAuditorRemainNullWithoutQueryingNullIds() {
        var row = record(1,1); row.setAuditorId(null);
        var content = item(1,"title");
        when(records.selectList(any())).thenReturn(List.of(row));
        when(source.findAll(any())).thenReturn(Map.of(1L,new AuditQuerySource.Item(null,content.view())));
        var page = queries.history(null,null,null,1,10);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getUser()).isNull();
        assertThat(page.getRecords().get(0).getAuditorName()).isNull();
        verifyNoInteractions(users);
    }
    @Test void unknownTypeFailsBeforeQuery() {
        assertThatThrownBy(() -> queries.pending("invalid",null,null,1,10)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(records,users);
    }
    @Test void invalidPaginationIsNormalized() {
        var page = AuditQueryService.slice(List.of(1,2),-1,10000);
        assertThat(page.getCurrent()).isEqualTo(1);assertThat(page.getSize()).isEqualTo(100);
        assertThat(page.getRecords()).containsExactly(1,2);
    }
    @Test void dailyHistoryUsesFullTextInsteadOfPendingPreview() {
        var row=AuditListDto.builder().targetType("daily").title("preview...").content("full content after preview").build();
        assertThat(new AuditQuerySource.Item(1L,row).historyTitle()).isEqualTo("full content after preview");
    }
    @Test void nullContentDoesNotCrashConversion() {
        assertThat(AuditQuerySources.shorten(null,100)).isNull();
        assertThat(AuditQuerySources.shorten("abcd",3)).isEqualTo("abc...");
    }
}
