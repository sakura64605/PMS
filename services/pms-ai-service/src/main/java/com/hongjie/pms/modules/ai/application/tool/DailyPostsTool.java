package com.hongjie.pms.modules.ai.application.tool;
import com.hongjie.pms.ai.contract.BaseTool;
import com.hongjie.pms.modules.ai.api.dto.ToolCall;
import com.hongjie.pms.aiservice.core.AiCoreClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
@RequiredArgsConstructor
public class DailyPostsTool implements BaseTool {
    private final AiCoreClient core;
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


    @Override public ToolCall execute(Map<String,Object> args, Long actor) {
        return core.tool(getName(),args,actor);
    }
}
