package com.hongjie.pms.refactor.privateMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrivateMessageCommittedListener {
    private final PrivateMessagePush push;
    @TransactionalEventListener
    public void committed(PrivateMessageCommitted event){
        try{push.send(event.recipient(),event.message());}
        catch(RuntimeException e){log.error("私信已提交，实时推送失败: messageId={}, recipient={}",event.message().getId(),event.recipient(),e);}
    }
}
