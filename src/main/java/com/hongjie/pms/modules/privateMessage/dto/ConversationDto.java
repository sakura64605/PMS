package com.hongjie.pms.modules.privateMessage.dto;

import com.hongjie.pms.modules.user.dto.UserSimpleDto;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ConversationDto {

    private Long conversationId;
    private UserSimpleDto otherUser;      // 对方用户信息
    private String lastMessage;           // 最后一条消息
    private LocalDateTime lastMessageTime;
    private Integer unreadCount;          // 当前用户未读数
}