package com.hongjie.pms.modules.message.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageMqDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 接收者用户ID */
    private Long userId;

    /** 发送者用户ID */
    private Long senderId;

    /** 消息类型 */
    private String type;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 业务ID */
    private Long businessId;

    /** 跳转链接 */
    private String link;

    /** 创建时间 */
    private LocalDateTime createTime;
}