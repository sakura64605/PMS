package com.hongjie.pms.refactor.notice;

import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.pojo.UserInfo;
import com.hongjie.pms.modules.notice.entity.Notice;
import com.hongjie.pms.modules.notice.mapper.NoticeMapper;
import com.hongjie.pms.refactor.notice.controller.NoticeAdminDetailController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NoticeAdminDetailControllerTest {
    final NoticeMapper mapper = mock(NoticeMapper.class);
    final NoticeAdminDetailController controller = new NoticeAdminDetailController(mapper);
    @AfterEach void cleanup() { UserContext.clear(); }
    @Test void anonymousAndOrdinaryUsersCannotReadDraft() {
        assertThatThrownBy(() -> controller.detail(7L)).isInstanceOf(BusinessException.class);
        UserContext.setUser(new UserInfo(2L,"user",0,null,null));
        assertThatThrownBy(() -> controller.detail(7L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(mapper);
    }
    @Test void administratorCanReadUnpublishedBodyWithoutWriting() {
        UserContext.setUser(new UserInfo(1L,"admin",1,null,null));
        Notice draft = new Notice(); draft.setId(7L); draft.setStatus(0); draft.setContent("draft body");
        when(mapper.selectById(7L)).thenReturn(draft);
        assertThat(controller.detail(7L).getData()).isSameAs(draft);
        verify(mapper).selectById(7L); verifyNoMoreInteractions(mapper);
    }
    @Test void missingNoticeIsRejected() {
        UserContext.setUser(new UserInfo(1L,"admin",1,null,null));
        assertThatThrownBy(() -> controller.detail(99L)).isInstanceOf(BusinessException.class);
    }
}
