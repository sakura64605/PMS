package com.hongjie.pms.modules.communication.notification.application;

import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.pojo.UserInfo;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.communication.notification.infrastructure.persistence.entity.UserMessage;
import com.hongjie.pms.modules.communication.notification.infrastructure.persistence.mapper.UserMessageMapper;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageInboxTest {
    UserMessageMapper messages;
    MessageInboxMapper writes;
    MessageInbox inbox;

    @BeforeEach
    void setup() {
        UserContext.setUser(new UserInfo(1L, "viewer", 0, null, null));
        messages = mock(UserMessageMapper.class);
        writes = mock(MessageInboxMapper.class);
        inbox = new MessageInbox(messages, writes);
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void anonymousCannotListOrReadAll() {
        UserContext.clear();
        assertThatThrownBy(() -> inbox.list(null, 1, 20, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> inbox.readAll(null)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(messages, writes);
    }

    @Test
    void spoofedInboxCannotBeQueried() {
        assertThatThrownBy(() -> inbox.unread(2L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> inbox.list(2L, 1, 20, null)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(messages, writes);
    }

    @Test
    void foreignReadCannotOverwriteMessage() {
        var row = new UserMessage();
        row.setUserId(2L);
        when(messages.selectById(10L)).thenReturn(row);
        assertThatThrownBy(() -> inbox.read(10L)).isInstanceOf(BusinessException.class);
        verify(writes).readOne(1L, 10L);
        verify(messages, never()).updateById(any(UserMessage.class));
    }

    @Test
    void alreadyReadIsIdempotent() {
        var row = new UserMessage();
        row.setUserId(1L);
        row.setIsRead(1);
        when(messages.selectById(10L)).thenReturn(row);
        inbox.read(10L);
        verify(messages, never()).updateById(any(UserMessage.class));
    }
}
