package com.hongjie.pms.AI.tool;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.AI.modules.dto.ToolCall;
import com.hongjie.pms.modules.notice.entity.Notice;
import com.hongjie.pms.modules.notice.mapper.NoticeMapper;
import com.hongjie.pms.modules.notice.mapper.NoticeReadRecordMapper;
import com.hongjie.pms.modules.notice.entity.NoticeReadRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class MyNotificationsTool implements BaseTool {

    private final NoticeMapper noticeMapper;
    private final NoticeReadRecordMapper readRecordMapper;

    @Override
    public String getName() { return "my_notifications"; }

    @Override
    public String getDescription() { return "查询当前用户的系统通知和未读消息概况"; }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        properties.put("limit", Map.of("type", "integer", "description", "返回数量，默认5"));
        params.put("properties", properties);
        return params;
    }

    @Override
    public ToolCall execute(Map<String, Object> args, Long userId) {
        int limit = args.containsKey("limit") ? ((Number) args.get("limit")).intValue() : 5;

        LambdaQueryWrapper<Notice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notice::getStatus, 1)
                .orderByDesc(Notice::getIsTop)
                .orderByDesc(Notice::getCreateTime)
                .last("LIMIT " + limit);

        List<Notice> notices = noticeMapper.selectList(wrapper);

        // 统计未读数量
        long unreadCount = 0;
        for (Notice notice : notices) {
            LambdaQueryWrapper<NoticeReadRecord> readWrapper = new LambdaQueryWrapper<>();
            readWrapper.eq(NoticeReadRecord::getNoticeId, notice.getId())
                    .eq(NoticeReadRecord::getUserId, userId);
            if (readRecordMapper.selectCount(readWrapper) == 0) {
                unreadCount++;
            }
        }

        StringBuilder result = new StringBuilder();
        if (notices.isEmpty()) {
            result.append("您当前没有系统通知。");
        } else {
            result.append("您有 ").append(unreadCount).append(" 条未读通知（共").append(notices.size()).append("条最新通知）：\n\n");
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm");
            for (int i = 0; i < notices.size(); i++) {
                Notice notice = notices.get(i);
                result.append(i + 1).append(". ");
                if (notice.getIsTop() != null && notice.getIsTop() == 1) result.append("🔝[置顶] ");
                result.append(notice.getTitle()).append("\n");
                result.append("   发布时间：").append(notice.getCreateTime().format(fmt)).append("\n\n");
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