package com.hongjie.pms.AI.tool;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.AI.modules.dto.ToolCall;
import com.hongjie.pms.modules.daily.entity.DailyPost;
import com.hongjie.pms.modules.daily.entity.Topic;
import com.hongjie.pms.modules.daily.mapper.DailyPostMapper;
import com.hongjie.pms.modules.daily.mapper.TopicMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyPostsTool implements BaseTool {

    private final DailyPostMapper dailyPostMapper;
    private final TopicMapper topicMapper;

    @Override
    public String getName() { return "search_daily_posts"; }

    @Override
    public String getDescription() { return "搜索宠友日记（社区动态），可按话题或关键词搜索"; }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        properties.put("keyword", Map.of("type", "string", "description", "搜索关键词，在日记内容中搜索"));
        properties.put("topic", Map.of("type", "string", "description", "话题名称，如'猫咪日常'"));
        properties.put("limit", Map.of("type", "integer", "description", "返回数量，默认5"));
        params.put("properties", properties);
        return params;
    }

    @Override
    public ToolCall execute(Map<String, Object> args, Long userId) {
        String keyword = (String) args.get("keyword");
        String topicName = (String) args.get("topic");
        int limit = args.containsKey("limit") ? ((Number) args.get("limit")).intValue() : 5;

        // 如果有话题名，先查话题ID
        Long topicId = null;
        if (topicName != null && !topicName.isEmpty()) {
            LambdaQueryWrapper<Topic> topicWrapper = new LambdaQueryWrapper<>();
            topicWrapper.like(Topic::getName, topicName).eq(Topic::getStatus, 1).last("LIMIT 1");
            Topic topic = topicMapper.selectOne(topicWrapper);
            if (topic != null) topicId = topic.getId();
        }

        LambdaQueryWrapper<DailyPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DailyPost::getStatus, 1).eq(DailyPost::getAuditStatus, 1);

        if (topicId != null) wrapper.eq(DailyPost::getTopicId, topicId);
        if (keyword != null && !keyword.isEmpty()) wrapper.like(DailyPost::getContent, keyword);

        wrapper.orderByDesc(DailyPost::getCreateTime).last("LIMIT " + limit);

        List<DailyPost> posts = dailyPostMapper.selectList(wrapper);

        StringBuilder result = new StringBuilder();
        if (posts.isEmpty()) {
            result.append("没有找到相关的宠友日记。");
            if (topicName != null) result.append(" 话题\"").append(topicName).append("\"下暂无内容。");
        } else {
            result.append("找到 ").append(posts.size()).append(" 条宠友日记：\n\n");
            for (int i = 0; i < posts.size(); i++) {
                DailyPost post = posts.get(i);
                String content = post.getContent() != null && post.getContent().length() > 120
                        ? post.getContent().substring(0, 120) + "..." : post.getContent();
                result.append(i + 1).append(". ").append(content != null ? content : "[无文字]").append("\n");
                if (post.getLocation() != null) result.append("   地点：").append(post.getLocation()).append("\n");
                result.append("   ❤️").append(post.getLikeCount()).append(" 💬").append(post.getCommentCount())
                        .append(" 👁️").append(post.getViewCount()).append("\n\n");
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