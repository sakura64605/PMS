package com.hongjie.pms.modules.communication.notification.application;

import com.hongjie.pms.modules.communication.notification.infrastructure.messaging.MessageMqDto;
import java.time.LocalDateTime;

/** Immutable snapshot: later changes to a caller's entity cannot alter the queued message. */
public record PendingNotification(Long userId,Long senderId,String type,String title,String content,
                                  Long businessId,String link,LocalDateTime createTime) {
    public MessageMqDto toMq(){return MessageMqDto.builder().userId(userId).senderId(senderId).type(type).title(title)
            .content(content).businessId(businessId).link(link).createTime(createTime).build();}
}
