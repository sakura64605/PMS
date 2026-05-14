package com.hongjie.pms.AI.modules.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ai_human_transfer")
public class AiHumanTransfer {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String sessionId;
    
    private Long userId;
    
    private Long adminId;
    
    private String reason;
    
    private Integer status;
    
    private LocalDateTime transferredAt;
    
    private LocalDateTime closedAt;
}