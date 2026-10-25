package com.hongjie.pms.modules.communication.notice.application;

import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.pojo.UserInfo;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.communication.notice.api.dto.response.NoticeDetailDto;
import com.hongjie.pms.modules.communication.notice.infrastructure.persistence.entity.Notice;
import com.hongjie.pms.modules.communication.notice.infrastructure.persistence.entity.NoticeReadRecord;
import com.hongjie.pms.modules.communication.notice.infrastructure.persistence.mapper.NoticeMapper;
import com.hongjie.pms.modules.communication.notice.infrastructure.persistence.mapper.NoticeReadRecordMapper;
import com.hongjie.pms.modules.communication.notice.application.converter.NoticeDtoConverter;
import com.hongjie.pms.modules.communication.notice.application.service.PmsNoticeServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NoticeDetailCacheTest {
    final NoticeMapper notices = mock(NoticeMapper.class);
    final NoticeReadRecordMapper reads = mock(NoticeReadRecordMapper.class);
    final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    final ValueOperations<String, Object> values = mock(ValueOperations.class);
    final PmsNoticeServiceImpl service = new PmsNoticeServiceImpl(notices, reads,
            new NoticeDtoConverter(), null, null, redis);

    NoticeDetailCacheTest() {
        when(redis.opsForValue()).thenReturn(values);
        // A legacy shared entry must never supply the current viewer's read state.
        when(values.get("notice:7")).thenReturn(NoticeDetailDto.builder().id(7L).isRead(true).build());
    }
    @AfterEach void cleanup() { UserContext.clear(); }
    Notice published() {
        Notice notice = new Notice(); notice.setId(7L); notice.setStatus(1); notice.setContent("fresh content");
        when(notices.selectById(7L)).thenReturn(notice);
        return notice;
    }

    @Test void otherViewerIsRecordedAndCachedDtoRemainsNeutral() {
        published();
        UserContext.setUser(new UserInfo(22L, "viewer", 0, null, null));
        assertThat(service.getByIdForUser(7L).getIsRead()).isTrue();
        verify(reads).exists(7L, 22L);
        verify(reads).insert(argThat((NoticeReadRecord r) -> r.getUserId().equals(22L) && r.getNoticeId().equals(7L)));
        verify(values).set(eq("notice:7"), argThat(dto -> dto instanceof NoticeDetailDto d
                && d.getIsRead() == null && "fresh content".equals(d.getContent())), eq(1800L), eq(TimeUnit.SECONDS));
    }

    @Test void anonymousViewerDoesNotInheritCachedReadStateOrWriteRecord() {
        published();
        assertThat(service.getByIdForUser(7L).getIsRead()).isFalse();
        verifyNoInteractions(reads);
    }

    @Test void cachedEntryCannotBypassUnpublishExpiryOrDeletion() {
        Notice notice = published();
        notice.setStatus(0);
        assertThatThrownBy(() -> service.getByIdForUser(7L)).isInstanceOf(BusinessException.class);
        notice.setStatus(1); notice.setExpireTime(LocalDateTime.now().minusMinutes(1));
        assertThatThrownBy(() -> service.getByIdForUser(7L)).isInstanceOf(BusinessException.class);
        when(notices.selectById(7L)).thenReturn(null);
        assertThatThrownBy(() -> service.getByIdForUser(7L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(reads);
    }
}
