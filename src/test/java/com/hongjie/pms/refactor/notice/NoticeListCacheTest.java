package com.hongjie.pms.refactor.notice;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.UserInfo;
import com.hongjie.pms.modules.notice.dto.response.NoticeListDto;
import com.hongjie.pms.modules.notice.entity.Notice;
import com.hongjie.pms.modules.notice.entity.NoticeReadRecord;
import com.hongjie.pms.modules.notice.mapper.NoticeMapper;
import com.hongjie.pms.modules.notice.mapper.NoticeReadRecordMapper;
import com.hongjie.pms.refactor.notice.converter.NoticeDtoConverter;
import com.hongjie.pms.refactor.notice.service.PmsNoticeServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NoticeListCacheTest {
    final NoticeMapper notices = mock(NoticeMapper.class);
    final NoticeReadRecordMapper reads = mock(NoticeReadRecordMapper.class);
    final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    final ValueOperations<String, Object> values = mock(ValueOperations.class);
    final AtomicReference<Object> shared = new AtomicReference<>();
    final PmsNoticeServiceImpl service = new PmsNoticeServiceImpl(notices, reads,
            new NoticeDtoConverter(), null, null, redis);
    final Notice notice = new Notice();

    @SuppressWarnings("unchecked")
    NoticeListCacheTest() {
        notice.setId(7L); notice.setStatus(1); notice.setTitle("notice");
        notice.setPublishTime(LocalDateTime.now().minusDays(1));
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("notice:list:1_10")).thenAnswer(i -> shared.get());
        doAnswer(i -> { shared.set(i.getArgument(1)); return null; }).when(values)
                .set(eq("notice:list:1_10"), any(), eq(600L), eq(TimeUnit.SECONDS));
        when(notices.selectPage(any(Page.class), any())).thenAnswer(i -> {
            Page<Notice> page = i.getArgument(0);
            page.setRecords(List.of(notice)); page.setTotal(1); return page;
        });
        when(reads.selectList(any())).thenReturn(List.of());
    }
    @AfterEach void cleanup() { UserContext.clear(); }
    void viewer(long id) { UserContext.setUser(new UserInfo(id, "viewer", 0, null, null)); }
    NoticeReadRecord record(long id) {
        NoticeReadRecord record = new NoticeReadRecord(); record.setNoticeId(7L); record.setUserId(id); return record;
    }
    @SuppressWarnings("unchecked") IPage<NoticeListDto> base() { return (IPage<NoticeListDto>) shared.get(); }

    @Test void warmByAThenBThenAnonymousUsesNeutralCacheAndIndependentCopies() {
        viewer(11L); when(reads.selectList(any())).thenReturn(List.of(record(11L)));
        IPage<NoticeListDto> a = service.listForUser(1, 10);
        assertThat(a.getRecords().get(0).getIsRead()).isTrue();
        assertThat(base().getRecords().get(0).getIsRead()).isNull();
        viewer(22L); when(reads.selectList(any())).thenReturn(List.of());
        IPage<NoticeListDto> b = service.listForUser(1, 10);
        assertThat(b.getRecords().get(0).getIsRead()).isNull();
        assertThat(b).isNotSameAs(a).isNotSameAs(base());
        assertThat(b.getRecords().get(0)).isNotSameAs(base().getRecords().get(0));
        UserContext.clear(); clearInvocations(reads);
        IPage<NoticeListDto> anon = service.listForUser(1, 10);
        assertThat(anon.getRecords().get(0).getIsRead()).isNull();
        assertThat(a.getRecords().get(0).getIsRead()).isTrue();
        assertThat(anon.getTotal()).isEqualTo(1);
        verifyNoInteractions(reads);
        verify(notices, times(1)).selectPage(any(Page.class), any());
    }

    @Test void readingDetailUpdatesReadStatusOnNextListCacheHit() {
        viewer(22L);
        assertThat(service.listForUser(1, 10).getRecords().get(0).getIsRead()).isNull();
        when(notices.selectById(7L)).thenReturn(notice);
        doAnswer(i -> {
            when(reads.selectList(any())).thenReturn(List.of(record(22L))); return 1;
        }).when(reads).insert(any(NoticeReadRecord.class));
        service.getByIdForUser(7L);
        assertThat(service.listForUser(1, 10).getRecords().get(0).getIsRead()).isTrue();
        assertThat(base().getRecords().get(0).getIsRead()).isNull();
        verify(notices, times(1)).selectPage(any(Page.class), any());
        verify(reads).exists(7L, 22L);
    }

    @Test void legacyPersonalizedCacheIsOverriddenWithoutMutatingIt() {
        NoticeListDto legacy = new NoticeDtoConverter().toListDto(notice, true);
        Page<NoticeListDto> page = new Page<>(1, 10, 1); page.setRecords(List.of(legacy)); shared.set(page);
        viewer(22L);
        assertThat(service.listForUser(1, 10).getRecords().get(0).getIsRead()).isNull();
        UserContext.clear();
        assertThat(service.listForUser(1, 10).getRecords().get(0).getIsRead()).isNull();
        assertThat(legacy.getIsRead()).isTrue();
        verifyNoInteractions(notices);
    }

    @Test void expiredCachedPageIsRefilledInsteadOfReturningExpiredRowOrWrongTotal() {
        NoticeListDto expired = new NoticeDtoConverter().toListDto(notice, null);
        expired.setExpireTime(LocalDateTime.now().minusSeconds(1));
        Page<NoticeListDto> page = new Page<>(1, 10, 1); page.setRecords(List.of(expired)); shared.set(page);
        when(notices.selectPage(any(Page.class), any())).thenAnswer(i -> {
            Page<Notice> empty = i.getArgument(0); empty.setRecords(List.of()); empty.setTotal(0); return empty;
        });
        IPage<NoticeListDto> response = service.listForUser(1, 10);
        assertThat(response.getRecords()).isEmpty(); assertThat(response.getTotal()).isZero();
        verify(notices).selectPage(any(Page.class), any());
    }
}
