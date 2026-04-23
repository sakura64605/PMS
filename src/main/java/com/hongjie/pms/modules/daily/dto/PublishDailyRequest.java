package com.hongjie.pms.modules.daily.dto;

import com.hongjie.pms.modules.daily.entity.DailyPost;
import lombok.Data;

import java.util.List;

@Data
public class PublishDailyRequest {
    private DailyPost dailyPost;
    private List<Long> topicIds;
}