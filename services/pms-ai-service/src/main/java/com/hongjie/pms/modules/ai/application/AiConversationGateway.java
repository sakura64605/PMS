package com.hongjie.pms.modules.ai.application;

import com.hongjie.pms.modules.ai.application.agent.AIAgentEngine;
import com.hongjie.pms.modules.ai.api.dto.request.AIAgentRequest;
import com.hongjie.pms.modules.ai.api.dto.response.AIAgentResponse;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AiConversationGateway {
    private final AiSessions sessions;
    private final AIAgentEngine engine;
    private final AiPetProfiles pets;
    private final AiPetRecords records;

    public AIAgentResponse process(Long actor, AIAgentRequest input) {
        return process(actor, input, "web");
    }

    public AIAgentResponse process(Long actor, AIAgentRequest input, String source) {
        var request = prepare(actor, input, source);
        try (var turn = AiTurnContext.open()) {
            var response = engine.process(request);
            response.setSessionId(request.getSessionId());
            if (turn.assistantId() != null) response.setMessageId(turn.assistantId());
            return response;
        }
    }

    public AIAgentRequest prepare(Long actor, AIAgentRequest input, String source) {
        AiSessions.actor(actor);
        if (input == null
                || input.getMessage() == null
                || input.getMessage().isBlank()
                || input.getMessage().length() > 8000)
            throw new BusinessException(ErrorCode.PARAM_ERROR, "消息长度应为1至8000字符");
        Long petId = input.getPetId();
        if (petId != null) pets.require(actor, petId);
        String id = input.getSessionId();
        boolean fresh = id == null || id.isBlank();
        if (id == null || id.isBlank()) id = sessions.create(actor, source);
        else if (!Objects.equals(
                sessions.require(id, actor, false, false).getStatus(), AiSessionState.ACTIVE.code))
            throw new BusinessException(ErrorCode.PARAM_ERROR, "会话正在人工处理中");
        if (fresh) {
            if (petId != null) pets.bind(actor, id, petId);
        } else {
            Long bound = sessions.require(id, actor, false, false).getPetId();
            if (petId != null && !Objects.equals(petId, bound))
                throw new BusinessException(409, "切换宠物需要开启新咨询");
            petId = bound;
        }
        String petContext = pets.context(actor, petId);
        if (petId != null) petContext += records.context(actor, petId, input.getMessage());
        var request = new AIAgentRequest();
        request.setPetId(petId);
        request.setPetContext(petContext);
        request.setUserId(actor);
        request.setSessionId(id);
        request.setMessage(input.getMessage());
        request.setStream(input.getStream());
        request.setHistory(input.getHistory());
        // Model/tool IO is deliberately outside a database transaction.
        return request;
    }
}
