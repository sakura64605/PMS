package com.hongjie.pms.refactor.ai;
import com.hongjie.pms.refactor.ai.api.ChatSessionService;
import com.hongjie.pms.AI.modules.mapper.*;
import com.hongjie.pms.AI.modules.entity.*;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Primary;
import java.util.*;
@Service @Primary
public class PmsChatSessionService implements ChatSessionService {
    private final AiSessions sessions;
    public PmsChatSessionService(AiSessions sessions){
        this.sessions=sessions;
    }
    private Long own(Long id){
        Long actor=UserContext.getUserId();AiSessions.actor(actor);
        if(!actor.equals(id))throw new BusinessException(ErrorCode.FORBIDDEN);return actor;
    }
    @Override public String createSession(Long id){return sessions.create(own(id),"web");}
    @Override public List<AiChatSession> getUserSessions(Long id){return sessions.list(own(id));}
    @Override public List<AiChatMessage> getSessionHistory(String id){return sessions.history(id,UserContext.getUserId());}
    @Override public void deleteSession(String id,Long actor){sessions.delete(id,own(actor));}
    @Override public void clearMemory(String id){sessions.clear(id,UserContext.getUserId());}
    @Override public void submitFeedback(String id,Integer score){sessions.feedback(id,score,UserContext.getUserId());}
    @Override public boolean checkSessionOwner(String id,Long actor){return sessions.owns(id,own(actor));}
}
