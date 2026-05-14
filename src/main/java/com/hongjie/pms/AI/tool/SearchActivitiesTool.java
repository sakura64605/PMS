package com.hongjie.pms.AI.tool;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.AI.modules.dto.ToolCall;
import com.hongjie.pms.modules.activity.entity.Activity;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
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
        String location = (String) args.get("location");
        int limit = args.containsKey("limit") ? (int) args.get("limit") : 5;

        LambdaQueryWrapper<Activity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Activity::getStatus, 1)
                .eq(Activity::getAuditStatus, 1)
                .orderByDesc(Activity::getCreateTime);

        if (location != null && !location.isEmpty()) {
            wrapper.like(Activity::getLocation, location);
        }
        wrapper.last("LIMIT " + limit);

        List<Activity> activities = activityMapper.selectList(wrapper);

        StringBuilder result = new StringBuilder();

        if (activities.isEmpty()) {
            result.append("没有找到符合条件的活动。");
            if (keyword != null && !keyword.isEmpty()) {
                result.append(" 建议尝试其他关键词或查看全部活动。");
            }
        } else {
            result.append("找到 ").append(activities.size()).append(" 个活动：\n\n");
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm");
            for (int i = 0; i < activities.size(); i++) {
                Activity act = activities.get(i);
                result.append(i + 1).append(". 【").append(act.getTitle()).append("】\n");
                result.append("   地点：").append(act.getLocation()).append("\n");
                result.append("   时间：").append(act.getStartTime().format(formatter))
                        .append(" 至 ").append(act.getEndTime().format(formatter)).append("\n");
                result.append("   报名：").append(act.getCurrentPeople()).append("/").append(act.getMaxPeople()).append("人\n");
                result.append("   描述：").append(act.getContent() != null && act.getContent().length() > 80 ?
                        act.getContent().substring(0, 80) + "..." : act.getContent()).append("\n\n");
            }
        }

        return ToolCall.builder()
                .id(UUID.randomUUID().toString())
                .name(getName())
                .arguments(JSON.toJSONString(args))
                .result(result.toString())
                .build();
    }
}