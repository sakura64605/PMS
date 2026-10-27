package com.hongjie.pms.modules.activity.application.ai;

import com.hongjie.pms.ai.contract.CoreQueryTool;
import com.hongjie.pms.ai.contract.ToolPaging;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.modules.ai.api.dto.ToolCall;
import com.hongjie.pms.modules.activity.infrastructure.persistence.entity.Activity;
import com.hongjie.pms.modules.activity.infrastructure.persistence.entity.ActivitySignup;
import com.hongjie.pms.modules.activity.infrastructure.persistence.mapper.ActivityMapper;
import com.hongjie.pms.modules.activity.infrastructure.persistence.mapper.ActivitySignupMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class MyActivitiesTool implements CoreQueryTool {

    private final ActivitySignupMapper signupMapper;
    private final ActivityMapper activityMapper;

    @Override
    public String getName() {
        return "my_activities";
    }

    @Override
    public ToolCall execute(Map<String, Object> args, Long userId) {
        String statusFilter = (String) args.get("status");
        int page = ToolPaging.page(args.get("page"));
        int pageSize = ToolPaging.pageSize(args.get("limit"));
        int offset = (page - 1) * pageSize;

        LambdaQueryWrapper<ActivitySignup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ActivitySignup::getUserId, userId);
        if ("signed".equals(statusFilter)) {
            wrapper.eq(ActivitySignup::getStatus, 1);
        } else if ("cancelled".equals(statusFilter)) {
            wrapper.eq(ActivitySignup::getStatus, 2);
        } else if ("checked".equals(statusFilter)) {
            wrapper.eq(ActivitySignup::getStatus, 3);
        }
        wrapper.orderByDesc(ActivitySignup::getCreateTime);

        long total = signupMapper.selectCount(wrapper);
        List<ActivitySignup> signups =
                signupMapper.selectList(wrapper.last("LIMIT " + offset + ", " + pageSize));
        int totalPages = total == 0 ? 0 : (int) ((total + pageSize - 1) / pageSize);

        StringBuilder result = new StringBuilder();
        if (signups.isEmpty()) {
            if (total == 0) {
                result.append("您还没有报名过任何活动。");
            } else {
                result.append("第 ")
                        .append(page)
                        .append(" 页没有更多报名记录了（共 ")
                        .append(total)
                        .append(" 条，已全部展示）。");
            }
        } else {
            result.append("您共报名 ").append(total).append(" 次活动");
            if (totalPages > 1) result.append("（共 ").append(totalPages).append(" 页）");
            result.append("，当前第 ").append(page).append(" 页：\n\n");

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm");
            for (int i = 0; i < signups.size(); i++) {
                ActivitySignup signup = signups.get(i);
                int no = offset + i + 1;
                Activity act = activityMapper.selectById(signup.getActivityId());
                String statusText =
                        switch (signup.getStatus()) {
                            case 1 -> "已报名";
                            case 2 -> "已取消";
                            case 3 -> "已签到";
                            case 4 -> "爽约";
                            default -> "未知";
                        };
                if (act != null) {
                    result.append(no)
                            .append(". 【")
                            .append(act.getTitle())
                            .append("】")
                            .append(statusText)
                            .append("\n");
                    result.append("   地点：").append(act.getLocation()).append("\n");
                    result.append("   时间：")
                            .append(act.getStartTime().format(fmt))
                            .append(" 至 ")
                            .append(act.getEndTime().format(fmt))
                            .append("\n");
                } else {
                    result.append(no).append(". [活动已删除] 状态：").append(statusText).append("\n");
                }
                result.append("   报名时间：").append(signup.getCreateTime().format(fmt)).append("\n\n");
            }
            ToolPaging.appendPagingFooter(result, page, totalPages, total, pageSize, "条");
        }

        return ToolCall.builder()
                .id(UUID.randomUUID().toString())
                .name(getName())
                .arguments(JSON.toJSONString(args))
                .result(result.toString())
                .build();
    }
}
