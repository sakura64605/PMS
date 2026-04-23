package com.hongjie.pms.modules.daily.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("daily_user_behavior")
public class DailyUserBehavior {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long targetId;
    private String actionType;
    private LocalDateTime actionTime;
}