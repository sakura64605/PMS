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

    private static final int DEFAULT_PAGE_SIZE = 5;
    private static final int MAX_PAGE_SIZE = 20;

    private final ActivityMapper activityMapper;

    @Override
    public String getName() {
        return "search_activities";
    }

    @Override
    public String getDescription() {
        return "搜索平台上的活动信息。注意：分页查询，每次只返回一页（默认5条）。"
                + "若结果提示还有更多页，请用 page 参数递增继续获取，不要凭当前一页断定没有更多。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        properties.put("keyword", Map.of("type", "string", "description", "搜索关键词"));
        properties.put("location", Map.of("type", "string", "description", "活动地点"));
        properties.put("page", Map.of("type", "integer", "description", "页码，从1开始，默认1"));
        properties.put("limit", Map.of("type", "integer", "description", "每页条数，默认5，最大20"));

        params.put("properties", properties);
        return params;
    }

    @Override
    public ToolCall execute(Map<String, Object> args, Long userId) {
        String keyword = (String) args.getOrDefault("keyword", "");
        String location = (String) args.get("location");
        int page = parsePositive(args.get("page"), 1);
        int pageSize = clampPageSize(args.get("limit"), DEFAULT_PAGE_SIZE);
        int offset = (page - 1) * pageSize;

        long total = activityMapper.selectCount(buildWrapper(keyword, location, false));
        List<Activity> activities = activityMapper.selectList(
                buildWrapper(keyword, location, true).last("LIMIT " + offset + ", " + pageSize));
        int totalPages = total == 0 ? 0 : (int) ((total + pageSize - 1) / pageSize);

        StringBuilder result = new StringBuilder();

        if (activities.isEmpty()) {
            if (total == 0) {
                result.append("没有找到符合条件的活动。");
                if (keyword != null && !keyword.isEmpty()) {
                    result.append(" 建议尝试其他关键词或查看全部活动。");
                }
            } else {
                result.append("第 ").append(page).append(" 页没有更多活动了（共 ").append(total).append(" 个，已全部展示）。");
            }
        } else {
            result.append("共找到 ").append(total).append(" 个活动");
            if (totalPages > 1) {
                result.append("（共 ").append(totalPages).append(" 页）");
            }
            result.append("，当前第 ").append(page).append(" 页，本页 ").append(activities.size()).append(" 个：\n\n");

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm");
            for (int i = 0; i < activities.size(); i++) {
                Activity act = activities.get(i);
                int no = offset + i + 1;
                result.append(no).append(". 【").append(act.getTitle()).append("】\n");
                result.append("   地点：").append(act.getLocation()).append("\n");
                result.append("   时间：").append(act.getStartTime().format(formatter))
                        .append(" 至 ").append(act.getEndTime().format(formatter)).append("\n");
                result.append("   报名：").append(act.getCurrentPeople()).append("/").append(act.getMaxPeople()).append("人\n");
                result.append("   描述：").append(act.getContent() != null && act.getContent().length() > 80 ?
                        act.getContent().substring(0, 80) + "..." : act.getContent()).append("\n\n");
            }
            appendPagingHint(result, page, totalPages, total, pageSize);
        }

        return ToolCall.builder()
                .id(UUID.randomUUID().toString())
                .name(getName())
                .arguments(JSON.toJSONString(args))
                .result(result.toString())
                .build();
    }

    private LambdaQueryWrapper<Activity> buildWrapper(String keyword, String location, boolean withOrder) {
        LambdaQueryWrapper<Activity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Activity::getStatus, 1)
                .eq(Activity::getAuditStatus, 1);
        if (location != null && !location.isEmpty()) {
            wrapper.like(Activity::getLocation, location);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Activity::getTitle, keyword)
                    .or().like(Activity::getContent, keyword));
        }
        if (withOrder) {
            wrapper.orderByDesc(Activity::getCreateTime);
        }
        return wrapper;
    }

    /** 结果末尾的分页引导 */
    static void appendPagingHint(StringBuilder sb, int page, int totalPages, long total, int pageSize) {
        if (page < totalPages) {
            long remaining = total - (long) page * pageSize;
            sb.append("还有 ").append(Math.max(0, remaining))
                    .append(" 个未展示。若用户想看更多，请再次调用本工具并传 page=").append(page + 1)
                    .append("（其余参数保持不变）。");
        } else {
            sb.append("已展示全部 ").append(total).append(" 个。");
        }
    }

    private static int parsePositive(Object value, int defaultValue) {
        if (value instanceof Number n) {
            return Math.max(1, n.intValue());
        }
        return defaultValue;
    }

    private static int clampPageSize(Object value, int defaultValue) {
        int size = value instanceof Number n ? n.intValue() : defaultValue;
        if (size < 1) return defaultValue;
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
