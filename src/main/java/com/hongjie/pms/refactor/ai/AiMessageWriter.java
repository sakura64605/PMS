package com.hongjie.pms.refactor.ai;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hongjie.pms.AI.modules.entity.*;
import com.hongjie.pms.AI.modules.mapper.*;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class AiMessageWriter {
    private final AiSessions sessions;
    private final AiChatMessageMapper messages;
    private final AiChatSessionMapper rows;
    private final AiSessionCache cache;
    @Transactional(propagation=Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    public String append(String sessionId,String role,String content,Long actor,Integer tokens,String tools){
        if(role==null||!Set.of("user","assistant").contains(role)||content==null
                ||content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>60000
                ||tokens!=null&&tokens<0||tools!=null&&!com.alibaba.fastjson2.JSON.isValid(tools))
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        var session=sessions.require(sessionId,actor,true,false);
        AiChatMessage row=new AiChatMessage();row.setSessionId(sessionId);row.setMessageId(UUID.randomUUID().toString());
        row.setRole(role);row.setContent(content);row.setTokensUsed(tokens);row.setToolCalls(tools);row.setCreatedAt(LocalDateTime.now());
        if(messages.insert(row)!=1)throw new BusinessException(ErrorCode.DB_ERROR);
        if(rows.update(null,new LambdaUpdateWrapper<AiChatSession>().eq(AiChatSession::getId,session.getId()).eq(AiChatSession::getUserId,actor)
                .in(AiChatSession::getStatus,List.of(1,3)).setSql("message_count = COALESCE(message_count, 0) + 1")
                .set(AiChatSession::getUpdatedAt,row.getCreatedAt()))!=1)throw new BusinessException(ErrorCode.DB_ERROR);
        cache.afterCommit(sessionId);return row.getMessageId();
    }
}
