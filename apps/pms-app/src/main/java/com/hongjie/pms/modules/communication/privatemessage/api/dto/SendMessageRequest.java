package com.hongjie.pms.modules.communication.privatemessage.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SendMessageRequest {

    @NotNull(message = "接收者ID不能为空")
    private Long toUserId;

    @NotBlank(message = "消息内容不能为空")
    private String content;

    @Pattern(regexp = "[A-Za-z0-9_-]{16,64}", message = "无效的客户端消息标识")
    private String clientMessageId;

    private Integer messageType = 1; // 1-文本 2-图片
}
