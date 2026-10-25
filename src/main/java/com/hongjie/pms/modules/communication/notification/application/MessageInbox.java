package com.hongjie.pms.modules.communication.notification.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.communication.notification.infrastructure.persistence.entity.UserMessage;
import com.hongjie.pms.modules.communication.notification.infrastructure.persistence.mapper.UserMessageMapper;
import com.hongjie.pms.shared.core.page.PageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MessageInbox {
    private final UserMessageMapper messages;
    private final MessageInboxMapper writes;
    public long unread(Long actor){actor(actor);return messages.selectCount(new LambdaQueryWrapper<UserMessage>().eq(UserMessage::getUserId,actor).eq(UserMessage::getIsRead,0));}
    public IPage<UserMessage> list(Long actor,Integer number,Integer size,String type){
        actor(actor);var query=new LambdaQueryWrapper<UserMessage>().eq(UserMessage::getUserId,actor);
        String filter=filter(type);if(filter!=null)query.eq(UserMessage::getType,filter);
        query.orderByDesc(UserMessage::getCreateTime).orderByDesc(UserMessage::getId);
        return messages.selectPage(PageQuery.of(number==null?null:number.longValue(),size==null||size<=0?20L:size.longValue()).toPage(),query);
    }
    @Transactional(rollbackFor=Exception.class)
    public void read(Long id){
        Long actor=current();if(id==null||id<=0)throw new BusinessException(ErrorCode.PARAM_ERROR);
        if(writes.readOne(actor,id)==1)return;
        var row=messages.selectById(id);if(row==null)throw new BusinessException(ErrorCode.NOT_FOUND,"消息不存在");
        if(!actor.equals(row.getUserId()))throw new BusinessException(ErrorCode.FORBIDDEN);
        // An already read message retains its first read timestamp.
    }
    @Transactional(rollbackFor=Exception.class)
    public void readAll(String type){writes.readAll(current(),filter(type));}
    private String filter(String type){return StringUtils.hasText(type)?type.trim():null;}
    private void actor(Long actor){if(!current().equals(actor))throw new BusinessException(ErrorCode.FORBIDDEN);}
    private Long current(){Long id=UserContext.getUserId();if(id==null)throw new BusinessException(ErrorCode.UNAUTHORIZED);return id;}
}
