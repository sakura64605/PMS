package com.hongjie.pms.modules.privateMessage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("private_message")
public class PrivateMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;
    private Long fromUserId;
    private Long toUserId;
    private Integer messageType;
    private String content;
    private Integer isRead;

    private Integer isDeletedByFrom;  // 发送者是否删除
    private Integer isDeletedByTo;    // 接收者是否删除

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}