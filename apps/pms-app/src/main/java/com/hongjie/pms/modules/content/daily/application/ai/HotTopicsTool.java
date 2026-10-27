package com.hongjie.pms.modules.content.daily.application.ai;

import com.hongjie.pms.ai.contract.CoreQueryTool;
import com.hongjie.pms.ai.contract.ToolPaging;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.modules.ai.api.dto.ToolCall;
import com.hongjie.pms.modules.content.daily.infrastructure.persistence.entity.Topic;
import com.hongjie.pms.modules.content.daily.infrastructure.persistence.mapper.TopicMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class HotTopicsTool implements CoreQueryTool {

    private final TopicMapper topicMapper;

    @Override
    public String getName() {
        return "hot_topics";
    }

    @Override
    public ToolCall execute(Map<String, Object> args, Long userId) {
        int page = ToolPaging.page(args.get("page"));
        int pageSize = ToolPaging.pageSize(args.get("limit"));
        int offset = (page - 1) * pageSize;

        LambdaQueryWrapper<Topic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Topic::getStatus, 1).orderByDesc(Topic::getHotScore);

        long total = topicMapper.selectCount(wrapper);
        List<Topic> topics =
                topicMapper.selectList(wrapper.last("LIMIT " + offset + ", " + pageSize));
        int totalPages = total == 0 ? 0 : (int) ((total + pageSize - 1) / pageSize);

        StringBuilder result = new StringBuilder();
        if (topics.isEmpty()) {
            result.append("当前暂无更多热门话题。");
        } else {
            result.append("共 ").append(total).append(" 个热门话题");
            if (totalPages > 1) result.append("（共 ").append(totalPages).append(" 页）");
            result.append("，当前第 ")
                    .append(page)
                    .append(" 页 TOP")
                    .append(topics.size())
                    .append("：\n\n");
            for (int i = 0; i < topics.size(); i++) {
                Topic topic = topics.get(i);
                int no = offset + i + 1;
                result.append(no).append(". ").append(topic.getName());
                if (topic.getDescription() != null)
                    result.append(" - ").append(topic.getDescription());
                result.append("\n");
                result.append("   📝")
                        .append(topic.getPostCount() != null ? topic.getPostCount() : 0)
                        .append("篇帖子");
                result.append(" 👁️")
                        .append(topic.getViewCount() != null ? topic.getViewCount() : 0)
                        .append("次浏览");
                if (topic.getHotScore() != null)
                    result.append(" 🔥热度").append(String.format("%.0f", topic.getHotScore()));
                result.append("\n\n");
            }
            ToolPaging.appendPagingFooter(result, page, totalPages, total, pageSize, "个");
        }

        return ToolCall.builder()
                .id(UUID.randomUUID().toString())
                .name(getName())
                .arguments(JSON.toJSONString(args))
                .result(result.toString())
                .build();
    }
}
