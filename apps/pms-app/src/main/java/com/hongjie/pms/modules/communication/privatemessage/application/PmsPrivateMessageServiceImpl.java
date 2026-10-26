package com.hongjie.pms.modules.communication.privatemessage.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.communication.privatemessage.api.dto.*;
import com.hongjie.pms.modules.communication.privatemessage.infrastructure.persistence.entity.*;
import com.hongjie.pms.modules.communication.privatemessage.infrastructure.persistence.mapper.*;
import com.hongjie.pms.modules.communication.privatemessage.api.service.PrivateMessageService;
import com.hongjie.pms.modules.identity.user.api.dto.UserSimpleDto;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.entity.User;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.UserMapper;
import com.hongjie.pms.shared.core.page.PageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Primary
@RequiredArgsConstructor
public class PmsPrivateMessageServiceImpl implements PrivateMessageService {
    private final PrivateConversationMapper conversations;
    private final PrivateMessageMapper messages;
    private final PrivateMessageWriteMapper writes;
    private final UserMapper users;
    private final ApplicationEventPublisher events;

    @Override @Transactional(rollbackFor=Exception.class)
    public MessageDto sendMessage(Long actor,SendMessageRequest request){
        actor(actor);
        if(request==null||!StringUtils.hasText(request.getContent())||request.getContent().codePointCount(0,request.getContent().length())>1000)
            throw new BusinessException(ErrorCode.PARAM_ERROR,"私信内容需为1到1000个字符");
        int type=request.getMessageType()==null?1:request.getMessageType();
        if(type!=1&&type!=2)throw new BusinessException(ErrorCode.PARAM_ERROR,"不支持的私信类型");
        String key=request.getClientMessageId();
        if(key!=null&&!key.matches("[A-Za-z0-9_-]{16,64}"))throw new BusinessException(ErrorCode.PARAM_ERROR,"无效的客户端消息标识");
        // pair locks both users in a stable order, serializing retries from the same sender.
        var conversation=pair(actor,request.getToUserId());
        if(key!=null){
            var existing=writes.byClientMessageId(actor,key);
            if(existing!=null){
                if(!Objects.equals(existing.getToUserId(),request.getToUserId())||!Objects.equals(existing.getContent(),request.getContent())||!Objects.equals(existing.getMessageType(),type))
                    throw new BusinessException(ErrorCode.PARAM_ERROR,"消息标识已用于另一条消息");
                if(Integer.valueOf(1).equals(existing.getIsDeletedByFrom()))throw new BusinessException(ErrorCode.PARAM_ERROR,"该消息已清空，请重新发送");
                return messageDto(existing,users.selectById(actor));
            }
        }
        var message=new PrivateMessage();message.setConversationId(conversation.getId());message.setFromUserId(actor);message.setToUserId(request.getToUserId());
        message.setMessageType(type);message.setContent(request.getContent());message.setIsRead(0);message.setIsDeletedByFrom(0);message.setIsDeletedByTo(0);message.setCreateTime(LocalDateTime.now());
        message.setClientMessageId(key);
        messages.insert(message);
        String content=request.getContent();String preview=content.substring(0,content.offsetByCodePoints(0,Math.min(500,content.codePointCount(0,content.length()))));
        writes.sent(conversation.getId(),request.getToUserId(),preview,message.getCreateTime());
        var result=messageDto(message,users.selectById(actor));events.publishEvent(new PrivateMessageCommitted(request.getToUserId(),result));return result;
    }
    @Override @Transactional(rollbackFor=Exception.class)
    public ConversationDto getOrCreateConversation(Long actor,Long target){actor(actor);var row=pair(actor,target);return conversationDto(writes.view(actor,row.getId()),users.selectById(target));}

    private PrivateConversation pair(Long actor,Long target){
        validId(target);if(actor.equals(target))throw new BusinessException(ErrorCode.PARAM_ERROR,"不能和自己聊天");
        long low=Math.min(actor,target),high=Math.max(actor,target);
        var first=writes.lockUser(low);var second=writes.lockUser(high);
        if(first==null||second==null)throw new BusinessException(ErrorCode.NOT_FOUND,"用户不存在");
        if(!Integer.valueOf(1).equals(first.getStatus())||!Integer.valueOf(1).equals(second.getStatus()))throw new BusinessException(ErrorCode.FORBIDDEN,"禁用用户不能建立会话或发送私信");
        var row=writes.pair(low,high);
        if(row==null){row=new PrivateConversation();row.setUserA(low);row.setUserB(high);row.setUnreadCountA(0);row.setUnreadCountB(0);row.setUpdateTime(LocalDateTime.now());conversations.insert(row);}
        return row;
    }
    @Override @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public IPage<ConversationDto> getConversationList(Long actor,Integer number,Integer size){
        actor(actor);var query=page(number,size);var result=new Page<ConversationDto>(query.normalizedPageNum(),query.normalizedPageSize(),writes.conversationCount(actor));
        var rows=writes.conversations(actor,(result.getCurrent()-1)*result.getSize(),result.getSize());
        Set<Long> ids=rows.stream().map(row->actor.equals(row.getUserA())?row.getUserB():row.getUserA()).collect(Collectors.toSet());
        Map<Long,User> people=ids.isEmpty()?Map.of():index(users.selectBatchIds(ids));
        result.setRecords(rows.stream().map(row->conversationDto(row,people.get(actor.equals(row.getUserA())?row.getUserB():row.getUserA()))).toList());return result;
    }
    @Override @Transactional(rollbackFor=Exception.class)
    public IPage<MessageDto> getMessageList(Long actor,Long id,Integer number,Integer size){
        actor(actor);owned(actor,id);read(actor,id);
        var query=page(number,size);var result=new Page<MessageDto>(query.normalizedPageNum(),query.normalizedPageSize(),writes.messageCount(actor,id));
        var rows=writes.messages(actor,id,(result.getCurrent()-1)*result.getSize(),result.getSize());
        Set<Long> ids=rows.stream().map(PrivateMessage::getFromUserId).collect(Collectors.toSet());Map<Long,User> people=ids.isEmpty()?Map.of():index(users.selectBatchIds(ids));
        result.setRecords(rows.stream().map(row->messageDto(row,people.get(row.getFromUserId()))).toList());return result;
    }
    @Override @Transactional(rollbackFor=Exception.class)
    public MessageCursorPage getMessageHistory(Long actor, Long id, Long beforeId, Long afterId, Integer pageSize) {
        actor(actor);
        if (beforeId != null) validId(beforeId);
        if (afterId != null) validId(afterId);
        if ((beforeId != null && afterId != null) || pageSize == null || pageSize < 1 || pageSize > 100)
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        owned(actor, id);
        read(actor, id);
        var found = afterId == null ? writes.olderMessages(actor, id, beforeId, pageSize + 1)
                : writes.newerMessages(actor, id, afterId, pageSize + 1);
        boolean more = found.size() > pageSize;
        var selected = found.subList(0, Math.min(found.size(), pageSize));
        Set<Long> authors = selected.stream().map(PrivateMessage::getFromUserId).collect(Collectors.toSet());
        Map<Long, User> people = authors.isEmpty() ? Map.of() : index(users.selectBatchIds(authors));
        var records = selected.stream().map(row -> messageDto(row, people.get(row.getFromUserId()))).toList();
        Long cursor = selected.isEmpty() ? null : selected.get(selected.size() - 1).getId();
        return new MessageCursorPage(records, cursor, more);
    }
    @Override public int getTotalUnreadCount(Long actor){actor(actor);return (int)Math.min(Integer.MAX_VALUE,writes.unread(actor));}
    @Override @Transactional(rollbackFor=Exception.class)
    public void markConversationRead(Long actor,Long id){actor(actor);owned(actor,id);read(actor,id);}
    private void read(Long actor,Long id){writes.markRead(actor,id);writes.clearUnread(actor,id);}
    @Override @Transactional(rollbackFor=Exception.class)
    public void clearMessages(Long actor,Long id){hide(actor,id);}
    @Override @Transactional(rollbackFor=Exception.class)
    public void deleteConversation(Long actor,Long id){hide(actor,id);}
    private void hide(Long actor,Long id){actor(actor);owned(actor,id);writes.hide(actor,id);writes.clearUnread(actor,id);}
    private PrivateConversation owned(Long actor,Long id){
        validId(id);var row=writes.lock(id);if(row==null)throw new BusinessException(ErrorCode.NOT_FOUND,"会话不存在");
        if(!actor.equals(row.getUserA())&&!actor.equals(row.getUserB()))throw new BusinessException(ErrorCode.FORBIDDEN,"无权操作该会话");return row;
    }
    private void actor(Long actor){Long current=UserContext.getUserId();if(current==null)throw new BusinessException(ErrorCode.UNAUTHORIZED);if(!current.equals(actor))throw new BusinessException(ErrorCode.FORBIDDEN);}
    private void validId(Long id){if(id==null||id<=0)throw new BusinessException(ErrorCode.PARAM_ERROR);}
    private PageQuery page(Integer number,Integer size){return PageQuery.of(number==null?null:number.longValue(),size==null||size<=0?20L:size.longValue());}
    private Map<Long,User> index(List<User> people){return people.stream().collect(Collectors.toMap(User::getId,Function.identity()));}
    private ConversationDto conversationDto(ConversationView row,User other){
        return ConversationDto.builder().conversationId(row.getId()).lastMessage(row.getLastMessage()).lastMessageTime(row.getLastMessageTime()).unreadCount(row.getUnreadCount())
                .otherUser(other==null?null:UserSimpleDto.builder().userId(other.getId()).username(other.getUserName()).nickname(other.getNickName()).avatar(other.getAvatar()).build()).build();
    }
    private MessageDto messageDto(PrivateMessage row,User author){return MessageDto.builder().id(row.getId()).clientMessageId(row.getClientMessageId()).fromUserId(row.getFromUserId()).toUserId(row.getToUserId()).messageType(row.getMessageType())
            .content(row.getContent()).isRead(row.getIsRead()).createTime(row.getCreateTime()).fromUserName(author==null?null:author.getUserName()).fromUserAvatar(author==null?null:author.getAvatar()).build();}
}
