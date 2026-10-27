package com.hongjie.pms.modules.communication.privatemessage.application;

import com.hongjie.pms.modules.communication.notification.api.websocket.WebSocketHandler;
import com.hongjie.pms.modules.communication.privatemessage.api.dto.MessageDto;
import org.springframework.stereotype.Component;

/** Adapter around the legacy static socket transport, replaceable in tests. */
@Component
public class PrivateMessagePush {
    public void send(Long recipient, MessageDto message) {
        WebSocketHandler.pushToUser(recipient, message);
    }
}
