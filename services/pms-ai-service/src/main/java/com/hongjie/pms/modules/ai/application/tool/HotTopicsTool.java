package com.hongjie.pms.modules.ai.application.tool;
import com.hongjie.pms.ai.contract.BaseTool;
import com.hongjie.pms.modules.ai.api.dto.ToolCall;
import com.hongjie.pms.aiservice.core.AiCoreClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
@RequiredArgsConstructor
public class HotTopicsTool implements BaseTool {
    private final AiCoreClient core;
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


    @Override public ToolCall execute(Map<String,Object> args, Long actor) {
        return core.tool(getName(),args,actor);
    }
}
