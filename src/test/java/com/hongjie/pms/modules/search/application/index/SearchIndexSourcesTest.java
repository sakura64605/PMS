package com.hongjie.pms.modules.search.application.index;
import com.hongjie.pms.modules.content.daily.infrastructure.persistence.mapper.DailyPostMapper;
import com.hongjie.pms.modules.content.daily.infrastructure.persistence.entity.DailyPost;
import com.hongjie.pms.modules.activity.infrastructure.persistence.mapper.ActivityMapper;
import com.hongjie.pms.modules.activity.infrastructure.persistence.entity.Activity;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.UserMapper;
import com.hongjie.pms.modules.content.daily.application.DailyAssembler;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class SearchIndexSourcesTest {
    @Test void dailyEmptyLinesAndDeletedRecordsAreSafeAndUseCurrentTopics(){
        var daily=mock(DailyPostMapper.class);var users=mock(UserMapper.class);var assembler=mock(DailyAssembler.class);
        var row=new DailyPost();row.setId(1L);row.setUserId(2L);row.setStatus(1);row.setContent("\n");row.setAuditStatus(0);
        var gone=new DailyPost();gone.setId(3L);gone.setStatus(0);
        when(daily.selectBatchIds(anyCollection())).thenReturn(List.of(row,gone));
        when(users.selectBatchIds(anyCollection())).thenReturn(List.of());when(assembler.topicMap(List.of(1L))).thenReturn(Map.of());
        var source=new SearchIndexSources().dailyIndexSource(daily,users,assembler);
        var result=source.load(List.of(1L,3L));assertThat(result).containsOnlyKeys(1L);
        assertThat(result.get(1L).getTitle()).isEmpty();assertThat(result.get(1L).getUserName()).isEqualTo("已注销");
        assertThat(result.get(1L).getAuditStatus()).isZero();verify(assembler).topicMap(List.of(1L));
    }
    @Test void deletedActivityIsOmittedAndPendingActivityIsRetainedForOwnerSearch(){
        var activities=mock(ActivityMapper.class);var users=mock(UserMapper.class);
        var row=new Activity();row.setId(1L);row.setUserId(2L);row.setDeleted(0);row.setAuditStatus(0);
        var gone=new Activity();gone.setId(3L);gone.setDeleted(1);
        when(activities.selectBatchIds(anyCollection())).thenReturn(List.of(row,gone));when(users.selectBatchIds(anyCollection())).thenReturn(List.of());
        var source=new SearchIndexSources().activityIndexSource(activities,users);
        assertThat(source.load(List.of(1L,3L))).containsOnlyKeys(1L);
    }
}
