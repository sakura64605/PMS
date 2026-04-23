// DailyTopicRel.java
package com.hongjie.pms.modules.daily.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("daily_topic_rel")
public class DailyTopicRel {
    private Long dailyId;
    private Long topicId;
}