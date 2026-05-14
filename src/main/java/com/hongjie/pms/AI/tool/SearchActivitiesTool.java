package com.hongjie.pms.AI.tool;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.AI.modules.dto.ToolCall;
import com.hongjie.pms.modules.activity.entity.Activity;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SearchActivitiesTool implements BaseTool {
    
    private final ActivityMapper activityMapper;
    
    @Override
    public String getName() {
        return "search_activities";
    }
    
    @Override
    public String getDescription() {
        return "搜索平台上的活动信息";
    }
    
    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("keyword", Map.of("type", "string", "description", "搜索关键词"));
        properties.put("location", Map.of("type", "string", "description", "活动地点"));
        properties.put("limit", Map.of("type", "integer", "description", "返回数量，默认5"));
        
        params.put("properties", properties);
        return params;
    }
    
    @Override
    public ToolCall execute(Map<String, Object> args, Long userId) {
        String keyword = (String) args.getOrDefault("keyword", "");
        int limit = args.containsKey("limit") ? (int) args.get("limit") : 5;
        
        LambdaQueryWrapper<Activity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Activity::getStatus, 1);
        wrapper.eq(Activity::getAuditStatus, 1);
        
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Activity::getTitle, keyword).or().like(Activity::getContent, keyword));
        }
        wrapper.last("LIMIT " + limit);
        
        long count = activityMapper.selectCount(wrapper);
        
        return ToolCall.builder()
                .id(UUID.randomUUID().toString())
                .name(getName())
                .arguments(JSON.toJSONString(args))
                .result("找到 " + count + " 条活动信息")
                .build();
    }
}