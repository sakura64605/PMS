package com.hongjie.pms.modules.ai.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hongjie.pms.modules.ai.infrastructure.persistence.entity.*;
import com.hongjie.pms.modules.ai.infrastructure.persistence.mapper.*;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class AiSessions {
    private final AiChatSessionMapper sessions;
    private final AiChatMessageMapper messages;
    private final AiHumanTransferMapper transfers;
    private final AiSessionCache cache;
    @Transactional public String create(Long actor,String source){
        actor(actor);
        var row=new AiChatSession();row.setSessionId(UUID.randomUUID().toString().replace("-",""));row.setUserId(actor);
        row.setTitle("新对话 "+LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")));
        row.setSource("websocket".equals(source)?"websocket":"web");row.setStatus(AiSessionState.ACTIVE.code);row.setMessageCount(0);
        row.setCreatedAt(LocalDateTime.now());row.setUpdatedAt(row.getCreatedAt());
        if(sessions.insert(row)!=1)throw new BusinessException(ErrorCode.DB_ERROR);
        return row.getSessionId();
    }
    public List<AiChatSession> list(Long actor){
        actor(actor);return sessions.selectList(new LambdaQueryWrapper<AiChatSession>().eq(AiChatSession::getUserId,actor)
                .in(AiChatSession::getStatus,List.of(AiSessionState.ACTIVE.code,AiSessionState.HUMAN.code))
                .orderByDesc(AiChatSession::getUpdatedAt).orderByDesc(AiChatSession::getId));
    }
    public AiChatSession require(String id,Long actor,boolean lock,boolean allowDeleted){
        actor(actor);
        if(id==null||id.isBlank()||id.length()>64)throw new BusinessException(ErrorCode.PARAM_ERROR);
        var query=new LambdaQueryWrapper<AiChatSession>().eq(AiChatSession::getSessionId,id);
        if(lock)query.last("FOR UPDATE");
        AiChatSession row=sessions.selectOne(query);
        if(row==null||!actor.equals(row.getUserId())||!allowDeleted&&!Set.of(1,3).contains(Objects.requireNonNullElse(row.getStatus(),-1)))
            throw new BusinessException(ErrorCode.NOT_FOUND,"会话不存在");
        return row;
    }
    public boolean owns(String id,Long actor){
        if(actor==null||id==null||id.isBlank()||id.length()>64)return false;
        return sessions.exists(new LambdaQueryWrapper<AiChatSession>().eq(AiChatSession::getSessionId,id)
                .eq(AiChatSession::getUserId,actor).in(AiChatSession::getStatus,List.of(AiSessionState.ACTIVE.code,AiSessionState.HUMAN.code)));
    }
    @Transactional(readOnly=true) public List<AiChatMessage> history(String id,Long actor){
        require(id,actor,false,false);
        return messages.selectList(new LambdaQueryWrapper<AiChatMessage>().eq(AiChatMessage::getSessionId,id)
                .orderByAsc(AiChatMessage::getCreatedAt).orderByAsc(AiChatMessage::getId));
    }
    @Transactional public void delete(String id,Long actor){
        var row=require(id,actor,true,true);
        if(Objects.equals(row.getStatus(),AiSessionState.DELETED.code))return;
        transfers.update(null,new LambdaUpdateWrapper<AiHumanTransfer>().eq(AiHumanTransfer::getSessionId,id)
                .in(AiHumanTransfer::getStatus,List.of(AiTransferState.REQUESTED.code,AiTransferState.ACCEPTED.code)).set(AiHumanTransfer::getStatus,AiTransferState.CLOSED.code).set(AiHumanTransfer::getClosedAt,LocalDateTime.now()));
        if(sessions.update(null,new LambdaUpdateWrapper<AiChatSession>().eq(AiChatSession::getId,row.getId())
                .set(AiChatSession::getStatus,AiSessionState.DELETED.code).set(AiChatSession::getUpdatedAt,LocalDateTime.now()))!=1)
            throw new BusinessException(ErrorCode.DB_ERROR);
        cache.afterCommit(id);
    }
    public void clear(String id,Long actor){
        require(id,actor,false,false);cache.clear(id);
    }
    @Transactional public void feedback(String messageId,Integer score,Long actor){
        actor(actor);
        if(messageId==null||messageId.isBlank()||messageId.length()>64||score==null||!Set.of(0,1).contains(score))
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        var candidates=messages.selectList(new LambdaQueryWrapper<AiChatMessage>().eq(AiChatMessage::getMessageId,messageId));
        if(candidates.size()!=1)throw new BusinessException(ErrorCode.NOT_FOUND);
        var message=candidates.get(0);require(message.getSessionId(),actor,true,false);
        if(!"assistant".equals(message.getRole()))throw new BusinessException(ErrorCode.PARAM_ERROR,"只能评价助手回复");
        if(messages.update(null,new LambdaUpdateWrapper<AiChatMessage>().eq(AiChatMessage::getId,message.getId())
                .eq(AiChatMessage::getSessionId,message.getSessionId()).eq(AiChatMessage::getRole,"assistant").set(AiChatMessage::getFeedback,score))!=1)
            throw new BusinessException(ErrorCode.DB_ERROR);
    }
    static void actor(Long actor){if(actor==null||actor<=0)throw new BusinessException(ErrorCode.UNAUTHORIZED);}
}
