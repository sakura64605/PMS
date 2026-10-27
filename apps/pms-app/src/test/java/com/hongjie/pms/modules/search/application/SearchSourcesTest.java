package com.hongjie.pms.modules.search.application;

import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.entity.PetPost;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.mapper.PetPostMapper;
import com.hongjie.pms.modules.activity.infrastructure.persistence.entity.Activity;
import com.hongjie.pms.modules.activity.infrastructure.persistence.mapper.ActivityMapper;
import com.hongjie.pms.modules.content.daily.infrastructure.persistence.entity.DailyPost;
import com.hongjie.pms.modules.content.daily.infrastructure.persistence.mapper.DailyPostMapper;
import com.hongjie.pms.modules.content.daily.application.DailyAssembler;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SearchSourcesTest {
    SearchSources config = new SearchSources();

    PetPost pet(long id, int status, int audit) {
        var p = new PetPost();
        p.setId(id);
        p.setUserId(7L);
        p.setStatus(status);
        p.setAuditStatus(audit);
        p.setTitle("new title");
        p.setLikeCount(99);
        return p;
    }

    @Test
    void petOnlyShowsApprovedPublishedOrCompletedToVisitors() {
        var mapper = mock(PetPostMapper.class);
        when(mapper.selectBatchIds(anyCollection()))
                .thenReturn(
                        List.of(
                                pet(1, 1, 1),
                                pet(2, 2, 1),
                                pet(3, 3, 1),
                                pet(4, 1, 0),
                                pet(5, -1, 1)));
        var source = config.petSearchSource(mapper);
        var result = source.load(Set.of(1L, 2L, 3L, 4L, 5L), null);
        assertThat(result).containsOnlyKeys(1L, 2L);
        assertThat(result.get(1L).getLikeCount()).isEqualTo(99);
        assertThat(source.load(Set.of(1L, 2L, 3L, 4L, 5L), 7L)).containsOnlyKeys(1L, 2L, 3L, 4L);
        verify(mapper, times(2)).selectBatchIds(anyCollection());
    }

    @Test
    void activityDeletedIsHiddenEvenFromOwnerAndOfflineHiddenFromOthers() {
        var mapper = mock(ActivityMapper.class);
        List<Activity> entities = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Activity a = new Activity();
            a.setId((long) i);
            a.setUserId(7L);
            a.setStatus(i);
            a.setAuditStatus(1);
            a.setDeleted(i == 4 ? 1 : 0);
            entities.add(a);
        }
        when(mapper.selectBatchIds(anyCollection())).thenReturn(entities);
        var source = config.activitySearchSource(mapper);
        assertThat(source.load(Set.of(1L), null)).containsOnlyKeys(0L, 1L, 2L);
        assertThat(source.load(Set.of(1L), 7L)).containsOnlyKeys(0L, 1L, 2L, 3L);
    }

    @Test
    void dailyPreviewAndDeletedRulesAndCurrentTopics() {
        var mapper = mock(DailyPostMapper.class);
        var assembler = mock(DailyAssembler.class);
        var approved = new DailyPost();
        approved.setId(1L);
        approved.setStatus(1);
        approved.setAuditStatus(1);
        approved.setUserId(7L);
        approved.setContent("Fresh cat\nbody");
        var pending = new DailyPost();
        pending.setId(2L);
        pending.setStatus(1);
        pending.setAuditStatus(0);
        pending.setUserId(7L);
        var deleted = new DailyPost();
        deleted.setId(3L);
        deleted.setStatus(0);
        deleted.setAuditStatus(1);
        deleted.setUserId(7L);
        when(mapper.selectBatchIds(anyCollection()))
                .thenReturn(List.of(approved, pending, deleted));
        when(assembler.topicMap(anyList())).thenReturn(Map.of());
        var source = config.dailySearchSource(mapper, assembler);
        assertThat(source.load(Set.of(1L, 2L, 3L), null)).containsOnlyKeys(1L);
        var result = source.load(Set.of(1L, 2L, 3L), 7L);
        assertThat(result).containsOnlyKeys(1L, 2L);
        assertThat(result.get(1L).getTitle()).isEqualTo("Fresh cat");
        assertThat(result.get(1L).getTopics()).isEmpty();
    }
}
