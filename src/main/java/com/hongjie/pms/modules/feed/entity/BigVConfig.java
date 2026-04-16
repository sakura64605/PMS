// BigVConfig.java
package com.hongjie.pms.modules.feed.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("big_v_config")
public class BigVConfig {
    
    @TableId
    private Long userId;
    private Integer fansCount;
    private Integer usePullMode;  // 1-拉模式 0-推模式
    private LocalDateTime updateTime;
}