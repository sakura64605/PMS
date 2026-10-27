package com.hongjie.pms.modules.communication.privatemessage.api.dto;

import com.hongjie.pms.modules.identity.user.api.dto.UserSimpleDto;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ConversationDto {

    private Long conversationId;
    private UserSimpleDto otherUser; // 对方用户信息
    private String lastMessage; // 最后一条消息
    private LocalDateTime lastMessageTime;
    private Integer unreadCount; // 当前用户未读数
}
