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
    public String getDescription() {
        return "搜索宠友日记（社区动态），可按话题或关键词搜索。注意：分页查询，每次只返回一页（默认5条），"
                + "若提示还有更多请用 page 递增继续，不要凭当前一页断定没有更多。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        properties.put("keyword", Map.of("type", "string", "description", "搜索关键词，在日记内容中搜索"));
        properties.put("topic", Map.of("type", "string", "description", "话题名称，如'猫咪日常'"));
        properties.put("page", Map.of("type", "integer", "description", "页码，从1开始，默认1"));
        properties.put("limit", Map.of("type", "integer", "description", "每页条数，默认5，最大20"));
        params.put("properties", properties);
        return params;
    }

    @Override
    public ToolCall execute(Map<String, Object> args, Long userId) {
        String keyword = (String) args.get("keyword");
        String topicName = (String) args.get("topic");
        int page = ToolPaging.page(args.get("page"));
        int pageSize = ToolPaging.pageSize(args.get("limit"));
        int offset = (page - 1) * pageSize;

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
        wrapper.orderByDesc(DailyPost::getCreateTime);

        long total = dailyPostMapper.selectCount(wrapper);
        List<DailyPost> posts = dailyPostMapper.selectList(
                wrapper.last("LIMIT " + offset + ", " + pageSize));
        int totalPages = total == 0 ? 0 : (int) ((total + pageSize - 1) / pageSize);

        StringBuilder result = new StringBuilder();
        if (posts.isEmpty()) {
            if (total == 0) {
                result.append("没有找到相关的宠友日记。");
                if (topicName != null) result.append(" 话题\"").append(topicName).append("\"下暂无内容。");
            } else {
                result.append("第 ").append(page).append(" 页没有更多日记了（共 ").append(total).append(" 条，已全部展示）。");
            }
        } else {
            result.append("共找到 ").append(total).append(" 条宠友日记");
            if (totalPages > 1) result.append("（共 ").append(totalPages).append(" 页）");
            result.append("，当前第 ").append(page).append(" 页，本页 ").append(posts.size()).append(" 条：\n\n");

            for (int i = 0; i < posts.size(); i++) {
                DailyPost post = posts.get(i);
                int no = offset + i + 1;
                String content = post.getContent() != null && post.getContent().length() > 120
                        ? post.getContent().substring(0, 120) + "..." : post.getContent();
                result.append(no).append(". ").append(content != null ? content : "[无文字]").append("\n");
                if (post.getLocation() != null) result.append("   地点：").append(post.getLocation()).append("\n");
                result.append("   ❤️").append(post.getLikeCount()).append(" 💬").append(post.getCommentCount())
                        .append(" 👁️").append(post.getViewCount()).append("\n\n");
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
