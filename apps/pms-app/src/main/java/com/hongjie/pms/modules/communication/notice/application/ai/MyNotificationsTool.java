package com.hongjie.pms.modules.communication.notice.application.ai;

import com.hongjie.pms.ai.contract.CoreQueryTool;
import com.hongjie.pms.ai.contract.ToolPaging;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.modules.ai.api.dto.ToolCall;
import com.hongjie.pms.modules.communication.notice.infrastructure.persistence.entity.Notice;
import com.hongjie.pms.modules.communication.notice.infrastructure.persistence.mapper.NoticeMapper;
import com.hongjie.pms.modules.communication.notice.infrastructure.persistence.mapper.NoticeReadRecordMapper;
import com.hongjie.pms.modules.communication.notice.infrastructure.persistence.entity.NoticeReadRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class MyNotificationsTool implements CoreQueryTool {

    private final NoticeMapper noticeMapper;
    private final NoticeReadRecordMapper readRecordMapper;

    @Override
    public String getName() {
        return "my_notifications";
    }

    @Override
    public ToolCall execute(Map<String, Object> args, Long userId) {
        int page = ToolPaging.page(args.get("page"));
        int pageSize = ToolPaging.pageSize(args.get("limit"));
        int offset = (page - 1) * pageSize;

        LambdaQueryWrapper<Notice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notice::getStatus, 1)
                .orderByDesc(Notice::getIsTop)
                .orderByDesc(Notice::getCreateTime);

        long total = noticeMapper.selectCount(wrapper);
        List<Notice> notices =
                noticeMapper.selectList(wrapper.last("LIMIT " + offset + ", " + pageSize));
        int totalPages = total == 0 ? 0 : (int) ((total + pageSize - 1) / pageSize);

        // 统计本页未读数量
        int unreadCount = 0;
        for (Notice notice : notices) {
            LambdaQueryWrapper<NoticeReadRecord> readWrapper = new LambdaQueryWrapper<>();
            readWrapper
                    .eq(NoticeReadRecord::getNoticeId, notice.getId())
                    .eq(NoticeReadRecord::getUserId, userId);
            if (readRecordMapper.selectCount(readWrapper) == 0) {
                unreadCount++;
            }
        }

        StringBuilder result = new StringBuilder();
        if (notices.isEmpty()) {
            if (total == 0) {
                result.append("您当前没有系统通知。");
            } else {
                result.append("第 ")
                        .append(page)
                        .append(" 页没有更多通知了（共 ")
                        .append(total)
                        .append(" 条，已全部展示）。");
            }
        } else {
            result.append("共 ").append(total).append(" 条通知");
            if (totalPages > 1) result.append("（共 ").append(totalPages).append(" 页）");
            result.append("，当前第 ")
                    .append(page)
                    .append(" 页，本页 ")
                    .append(unreadCount)
                    .append(" 条未读：\n\n");

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm");
            for (int i = 0; i < notices.size(); i++) {
                Notice notice = notices.get(i);
                int no = offset + i + 1;
                result.append(no).append(". ");
                if (notice.getIsTop() != null && notice.getIsTop() == 1) result.append("🔝[置顶] ");
                result.append(notice.getTitle()).append("\n");
                result.append("   发布时间：").append(notice.getCreateTime().format(fmt)).append("\n\n");
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
