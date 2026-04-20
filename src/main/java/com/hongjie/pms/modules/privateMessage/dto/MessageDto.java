package com.hongjie.pms.modules.privateMessage.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class MessageDto {

    private Long id;
    private Long fromUserId;
    private String fromUserName;
    private String fromUserAvatar;
    private Long toUserId;
    private Integer messageType;
    private String content;
    private Integer isRead;
    private LocalDateTime createTime;
}