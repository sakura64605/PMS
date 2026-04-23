package com.hongjie.pms.modules.daily.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("daily_user_interest")
public class DailyUserInterest {
    @TableId
    private Long userId;
    private String interestJson;
    private LocalDateTime updateTime;
}