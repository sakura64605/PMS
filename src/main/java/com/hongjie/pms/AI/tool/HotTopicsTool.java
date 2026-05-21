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
    public String getDescription() { return "查询平台当前热门话题"; }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        properties.put("limit", Map.of("type", "integer", "description", "返回数量，默认10"));
        params.put("properties", properties);
        return params;
    }

    @Override
    public ToolCall execute(Map<String, Object> args, Long userId) {
        int limit = args.containsKey("limit") ? ((Number) args.get("limit")).intValue() : 10;

        LambdaQueryWrapper<Topic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Topic::getStatus, 1)
                .orderByDesc(Topic::getHotScore)
                .last("LIMIT " + limit);

        List<Topic> topics = topicMapper.selectList(wrapper);

        StringBuilder result = new StringBuilder();
        if (topics.isEmpty()) {
            result.append("当前暂无热门话题。");
        } else {
            result.append("平台当前热门话题 TOP").append(topics.size()).append("：\n\n");
            for (int i = 0; i < topics.size(); i++) {
                Topic topic = topics.get(i);
                result.append(i + 1).append(". ").append(topic.getName());
                if (topic.getDescription() != null) result.append(" - ").append(topic.getDescription());
                result.append("\n");
                result.append("   📝").append(topic.getPostCount() != null ? topic.getPostCount() : 0).append("篇帖子");
                result.append(" 👁️").append(topic.getViewCount() != null ? topic.getViewCount() : 0).append("次浏览");
                if (topic.getHotScore() != null) result.append(" 🔥热度").append(String.format("%.0f", topic.getHotScore()));
                result.append("\n\n");
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