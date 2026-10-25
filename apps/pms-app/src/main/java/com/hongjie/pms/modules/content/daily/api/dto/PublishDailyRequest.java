package com.hongjie.pms.modules.content.daily.api.dto;

import com.hongjie.pms.modules.content.daily.infrastructure.persistence.entity.DailyPost;
import lombok.Data;

import java.util.List;

@Data
public class PublishDailyRequest {
    private DailyPost dailyPost;
    private List<Long> topicIds;
}