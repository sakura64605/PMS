package com.hongjie.pms.common.mq;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DelayMessageDto {
    
    /** 消息类型 */
    private String type;
    
    /** 业务ID */
    private Long businessId;
    
    /** 执行时间（时间戳） */
    private Long executeTime;
    
    /** 额外参数 */
    private String params;
}