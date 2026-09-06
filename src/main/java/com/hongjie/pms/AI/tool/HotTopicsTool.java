package com.hongjie.pms.AI.tool;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.AI.modules.dto.ToolCall;
import com.hongjie.pms.modules.daily.entity.Topic;
import com.hongjie.pms.modules.daily.mapper.TopicMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class HotTopicsTool implements BaseTool {

    private final TopicMapper topicMapper;

    @Override
    public String getName() { return "hot_topics"; }

    @Override
    public String getDescription() {
        return "查询平台当前热门话题（按热度排序）。支持分页查看更多：默认返回 TOP10，page 参数可翻取后续。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        properties.put("page", Map.of("type", "integer", "description", "页码，从1开始，默认1"));
        properties.put("limit", Map.of("type", "integer", "description", "每页条数，默认10，最大20"));
        params.put("properties", properties);
        return params;
    }

    @Override
    public ToolCall execute(Map<String, Object> args, Long userId) {
        int page = ToolPaging.page(args.get("page"));
        int pageSize = ToolPaging.pageSize(args.get("limit"));
        int offset = (page - 1) * pageSize;

        LambdaQueryWrapper<Topic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Topic::getStatus, 1)
                .orderByDesc(Topic::getHotScore);

        long total = topicMapper.selectCount(wrapper);
        List<Topic> topics = topicMapper.selectList(
                wrapper.last("LIMIT " + offset + ", " + pageSize));
        int totalPages = total == 0 ? 0 : (int) ((total + pageSize - 1) / pageSize);

        StringBuilder result = new StringBuilder();
        if (topics.isEmpty()) {
            result.append("当前暂无更多热门话题。");
        } else {
            result.append("共 ").append(total).append(" 个热门话题");
            if (totalPages > 1) result.append("（共 ").append(totalPages).append(" 页）");
            result.append("，当前第 ").append(page).append(" 页 TOP").append(topics.size()).append("：\n\n");
            for (int i = 0; i < topics.size(); i++) {
                Topic topic = topics.get(i);
                int no = offset + i + 1;
                result.append(no).append(". ").append(topic.getName());
                if (topic.getDescription() != null) result.append(" - ").append(topic.getDescription());
                result.append("\n");
                result.append("   📝").append(topic.getPostCount() != null ? topic.getPostCount() : 0).append("篇帖子");
                result.append(" 👁️").append(topic.getViewCount() != null ? topic.getViewCount() : 0).append("次浏览");
                if (topic.getHotScore() != null) result.append(" 🔥热度").append(String.format("%.0f", topic.getHotScore()));
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