package com.hongjie.pms.AI.tool;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.AI.modules.dto.ToolCall;
import com.hongjie.pms.modules.activity.entity.Activity;
import com.hongjie.pms.modules.activity.entity.ActivitySignup;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.activity.mapper.ActivitySignupMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class MyActivitiesTool implements BaseTool {

    private final ActivitySignupMapper signupMapper;
    private final ActivityMapper activityMapper;

    @Override
    public String getName() { return "my_activities"; }

    @Override
    public String getDescription() { return "查询当前用户报名的活动列表"; }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        properties.put("status", Map.of("type", "string", "description", "报名状态: signed-已报名, cancelled-已取消, checked-已签到, 不传则查全部"));
        properties.put("limit", Map.of("type", "integer", "description", "返回数量，默认5"));
        params.put("properties", properties);
        return params;
    }

    @Override
    public ToolCall execute(Map<String, Object> args, Long userId) {
        String statusFilter = (String) args.get("status");
        int limit = args.containsKey("limit") ? ((Number) args.get("limit")).intValue() : 5;

        LambdaQueryWrapper<ActivitySignup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ActivitySignup::getUserId, userId);
        if ("signed".equals(statusFilter)) {
            wrapper.eq(ActivitySignup::getStatus, 1);
        } else if ("cancelled".equals(statusFilter)) {
            wrapper.eq(ActivitySignup::getStatus, 2);
        } else if ("checked".equals(statusFilter)) {
            wrapper.eq(ActivitySignup::getStatus, 3);
        }
        wrapper.orderByDesc(ActivitySignup::getCreateTime).last("LIMIT " + limit);

        List<ActivitySignup> signups = signupMapper.selectList(wrapper);

        StringBuilder result = new StringBuilder();
        if (signups.isEmpty()) {
            result.append("您还没有报名过任何活动。");
        } else {
            result.append("您最近报名的活动：\n\n");
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm");
            for (int i = 0; i < signups.size(); i++) {
                ActivitySignup signup = signups.get(i);
                Activity act = activityMapper.selectById(signup.getActivityId());
                String statusText = switch (signup.getStatus()) {
                    case 1 -> "已报名";
                    case 2 -> "已取消";
                    case 3 -> "已签到";
                    case 4 -> "爽约";
                    default -> "未知";
                };
                if (act != null) {
                    result.append(i + 1).append(". 【").append(act.getTitle()).append("】").append(statusText).append("\n");
                    result.append("   地点：").append(act.getLocation()).append("\n");
                    result.append("   时间：").append(act.getStartTime().format(fmt))
                            .append(" 至 ").append(act.getEndTime().format(fmt)).append("\n");
                } else {
                    result.append(i + 1).append(". [活动已删除] 状态：").append(statusText).append("\n");
                }
                result.append("   报名时间：").append(signup.getCreateTime().format(fmt)).append("\n\n");
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