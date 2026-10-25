package com.hongjie.pms.modules.ai.application;
import com.hongjie.pms.modules.ai.application.agent.AIAgentEngine;
import com.hongjie.pms.modules.ai.api.dto.request.AIAgentRequest;
import com.hongjie.pms.modules.ai.api.dto.response.AIAgentResponse;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Objects;

@Service @RequiredArgsConstructor
public class AiConversationGateway {
    private final AiSessions sessions;
    private final AIAgentEngine engine;
    public AIAgentResponse process(Long actor,AIAgentRequest input){return process(actor,input,"web");}
    public AIAgentResponse process(Long actor,AIAgentRequest input,String source){
        var request=prepare(actor,input,source);
        try(var turn=AiTurnContext.open()){
            var response=engine.process(request);response.setSessionId(request.getSessionId());
            if(turn.assistantId()!=null)response.setMessageId(turn.assistantId());
            return response;
        }
    }
    public AIAgentRequest prepare(Long actor,AIAgentRequest input,String source){
        AiSessions.actor(actor);
        if(input==null||input.getMessage()==null||input.getMessage().isBlank()||input.getMessage().length()>8000)
            throw new BusinessException(ErrorCode.PARAM_ERROR,"消息长度应为1至8000字符");
        String id=input.getSessionId();
        if(id==null||id.isBlank())id=sessions.create(actor,source);
        else if(!Objects.equals(sessions.require(id,actor,false,false).getStatus(),AiSessionState.ACTIVE.code))
            throw new BusinessException(ErrorCode.PARAM_ERROR,"会话正在人工处理中");
        var request=new AIAgentRequest();request.setUserId(actor);request.setSessionId(id);request.setMessage(input.getMessage());
        request.setStream(input.getStream());request.setHistory(input.getHistory());
        // Model/tool IO is deliberately outside a database transaction.
        return request;
    }
}
