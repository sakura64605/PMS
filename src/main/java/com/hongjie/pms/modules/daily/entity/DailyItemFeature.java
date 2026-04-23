package com.hongjie.pms.modules.daily.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("daily_item_feature")
public class DailyItemFeature {
    @TableId
    private Long dailyId;
    private String topicIds;
    private String topicTags;
    private Double hotScore;
    private Double freshScore;
    private Double qualityScore;
    private LocalDateTime updateTime;
}