package com.hongjie.pms.modules.message.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_message")
public class UserMessage {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;          // 接收者
    private Long senderId;        // 发送者
    
    private String type;          // 消息类型
    private String title;         // 标题
    private String content;       // 内容
    private Long businessId;      // 业务ID
    private String link;          // 跳转链接
    
    private Integer isRead;       // 是否已读
    private LocalDateTime readTime; // 阅读时间
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}