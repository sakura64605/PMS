package com.hongjie.pms.refactor.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.refactor.ai.api.ChatMemoryService;
import com.hongjie.pms.AI.modules.mapper.AiChatMessageMapper;
import com.hongjie.pms.AI.modules.entity.AiChatMessage;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import java.time.ZoneId;
import java.util.*;

@Service @Primary
public class PmsChatMemoryService implements ChatMemoryService {
    private final AiChatMessageMapper messages;
    private final AiMessageWriter writer;
    private final AiSessionCache cache;
    public PmsChatMemoryService(AiChatMessageMapper messages, AiMessageWriter writer, AiSessionCache cache){
        this.messages=messages;this.writer=writer;this.cache=cache;
    }
    @Override public void saveMessage(String id,String role,String content,Long actor){saveMessage(id,role,content,actor,null,null);}
    @Override public void saveMessage(String id,String role,String content,Long actor,Integer tokens){saveMessage(id,role,content,actor,tokens,null);}
    @Override public void saveMessage(String id,String role,String content,Long actor,Integer tokens,String tools){
        String messageId=writer.append(id,role,content,actor,tokens,tools);AiTurnContext.saved(role,messageId);
    }
    @Override public List<MemoryMessage> getRecentMessages(String id,int rounds){
        if(id==null||id.isBlank()||id.length()>64||rounds<0)throw new BusinessException(ErrorCode.PARAM_ERROR);
        if(rounds==0)return List.of();
        var rows=messages.selectList(new LambdaQueryWrapper<AiChatMessage>().eq(AiChatMessage::getSessionId,id)
                .orderByDesc(AiChatMessage::getCreatedAt).orderByDesc(AiChatMessage::getId).last("LIMIT "+(Math.min(100,rounds)*2)));
        List<MemoryMessage> result=new ArrayList<>();
        for(int i=rows.size()-1;i>=0;i--){
            var row=rows.get(i);long time=row.getCreatedAt()==null?0:row.getCreatedAt().atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
            result.add(new MemoryMessage(row.getRole(),row.getContent(),time));
        }
        return result;
    }
    @Override public void clearMemory(String id){cache.clear(id);}
}
