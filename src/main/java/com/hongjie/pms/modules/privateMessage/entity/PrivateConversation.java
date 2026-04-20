package com.hongjie.pms.modules.privateMessage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("private_conversation")
public class PrivateConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userA;
    private Long userB;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private Integer unreadCountA;
    private Integer unreadCountB;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}