package com.hongjie.pms.modules.communication.privatemessage.application;

import lombok.Data;
import java.time.LocalDateTime;

/** Viewer-specific preview; never overwrite the shared conversation preview when one side clears. */
@Data
public class ConversationView {
    private Long id;
    private Long userA;
    private Long userB;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private Integer unreadCount;
}
