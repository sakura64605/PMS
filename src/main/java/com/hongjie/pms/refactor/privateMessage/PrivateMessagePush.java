package com.hongjie.pms.refactor.privateMessage;

import com.hongjie.pms.modules.message.websocket.WebSocketHandler;
import com.hongjie.pms.modules.privateMessage.dto.MessageDto;
import org.springframework.stereotype.Component;

/** Adapter around the legacy static socket transport, replaceable in tests. */
@Component
public class PrivateMessagePush {
    public void send(Long recipient,MessageDto message){WebSocketHandler.pushToUser(recipient,message);}
}
