package com.hongjie.pms.modules.communication.privatemessage.application;
import com.hongjie.pms.modules.communication.privatemessage.infrastructure.persistence.entity.PrivateMessage;

import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.pojo.UserInfo;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.communication.privatemessage.api.dto.SendMessageRequest;
import com.hongjie.pms.modules.communication.privatemessage.infrastructure.persistence.entity.PrivateConversation;
import com.hongjie.pms.modules.communication.privatemessage.infrastructure.persistence.mapper.*;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.entity.User;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.UserMapper;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrivateMessageServiceTest {
    PrivateConversationMapper conversations;PrivateMessageMapper messages;PrivateMessageWriteMapper writes;UserMapper users;ApplicationEventPublisher events;PmsPrivateMessageServiceImpl service;
    @BeforeEach void setup(){
        UserContext.setUser(new UserInfo(1L,"actor",0,null,null));conversations=mock(PrivateConversationMapper.class);messages=mock(PrivateMessageMapper.class);writes=mock(PrivateMessageWriteMapper.class);users=mock(UserMapper.class);events=mock(ApplicationEventPublisher.class);
        service=new PmsPrivateMessageServiceImpl(conversations,messages,writes,users,events);
        var row=new PrivateConversation();row.setId(10L);row.setUserA(1L);row.setUserB(2L);when(writes.lock(10L)).thenReturn(row);when(writes.pair(1L,2L)).thenReturn(row);
        for(long id:new long[]{1,2}){var u=new User();u.setId(id);u.setStatus(1);when(writes.lockUser(id)).thenReturn(u);}
    }
    @AfterEach void clear(){UserContext.clear();}
    SendMessageRequest request(){var r=new SendMessageRequest();r.setToUserId(2L);r.setContent("hello");return r;}
    @Test void spoofedActorFailsBeforeDatabaseAccess(){assertThatThrownBy(()->service.sendMessage(2L,request())).isInstanceOf(BusinessException.class);verifyNoInteractions(writes,messages,events);}
    @Test void anonymousReadFailsBeforeDatabaseAccess(){UserContext.clear();assertThatThrownBy(()->service.getConversationList(1L,1,20)).isInstanceOf(BusinessException.class);verifyNoInteractions(writes);}
    @Test void outsiderCannotReadMessages(){UserContext.setUser(new UserInfo(3L,"other",0,null,null));assertThatThrownBy(()->service.getMessageList(3L,10L,1,20)).isInstanceOf(BusinessException.class);verify(writes,never()).messages(anyLong(),anyLong(),anyLong(),anyLong());verify(writes,never()).markRead(anyLong(),anyLong());}
    @Test void outsiderCannotClearOrDelete(){UserContext.setUser(new UserInfo(3L,"other",0,null,null));assertThatThrownBy(()->service.clearMessages(3L,10L)).isInstanceOf(BusinessException.class);assertThatThrownBy(()->service.deleteConversation(3L,10L)).isInstanceOf(BusinessException.class);verify(writes,never()).hide(anyLong(),anyLong());verifyNoInteractions(conversations);}
    @Test void selfSendRejected(){var r=request();r.setToUserId(1L);assertThatThrownBy(()->service.sendMessage(1L,r)).isInstanceOf(BusinessException.class);verifyNoInteractions(writes,messages);}
    @Test void unsupportedMessageTypeRejected(){var r=request();r.setMessageType(9);assertThatThrownBy(()->service.sendMessage(1L,r)).isInstanceOf(BusinessException.class);verifyNoInteractions(writes,messages);}
    @Test void contentOverDatabaseLimitRejected(){var r=request();r.setContent("x".repeat(1001));assertThatThrownBy(()->service.sendMessage(1L,r)).isInstanceOf(BusinessException.class);verifyNoInteractions(writes,messages);}
    @Test void deletingOnlyHidesCurrentSideAndPreservesSharedConversation(){service.deleteConversation(1L,10L);verify(writes).hide(1L,10L);verify(writes).clearUnread(1L,10L);verifyNoInteractions(conversations,messages,events);}
    @Test void failedMessageInsertNeverPublishes(){doThrow(new IllegalStateException("insert failure")).when(messages).insert(any(PrivateMessage.class));assertThatThrownBy(()->service.sendMessage(1L,request())).isInstanceOf(IllegalStateException.class);verifyNoInteractions(events);verify(writes,never()).sent(anyLong(),anyLong(),anyString(),any());}
    @Test void cursorReadRejectsOutsiderBeforeReadingOrMarkingMessages(){
        UserContext.setUser(new UserInfo(3L,"other",0,null,null));
        assertThatThrownBy(()->service.getMessageHistory(3L,10L,null,null,30)).isInstanceOf(BusinessException.class);
        verify(writes,never()).olderMessages(anyLong(),anyLong(),any(),anyInt());
        verify(writes,never()).markRead(anyLong(),anyLong());
    }
    @Test void invalidCursorOrUnboundedBatchIsRejectedBeforeDatabaseAccess(){
        assertThatThrownBy(()->service.getMessageHistory(1L,10L,0L,null,30)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.getMessageHistory(1L,10L,1L,2L,30)).isInstanceOf(BusinessException.class);
        for(int size:new int[]{0,-1,101})assertThatThrownBy(()->service.getMessageHistory(1L,10L,null,null,size)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(writes);
    }
}
