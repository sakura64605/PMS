package com.hongjie.pms.common.punishment.scheduler;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("delay_task")
public class DelayTask {
    private Long id;
    private String taskType;
    private Long businessId;
    private LocalDateTime executeTime;
    private Integer status;
    private LocalDateTime createTime;
}