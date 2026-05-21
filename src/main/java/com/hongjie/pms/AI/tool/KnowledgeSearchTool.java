package com.hongjie.pms.AI.tool;

import com.alibaba.fastjson2.JSON;
import com.hongjie.pms.AI.modules.dto.ToolCall;
import com.hongjie.pms.AI.rag.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeSearchTool implements BaseTool {

    private final KnowledgeBaseService knowledgeBaseService;

    @Override
    public String getName() { return "search_knowledge"; }

    @Override
    public String getDescription() {
        return "搜索平台知识库（FAQ、使用指南、养宠知识等）。当用户询问平台操作问题或养宠知识时使用";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        properties.put("question", Map.of("type", "string", "description", "用户的问题，用于搜索相关知识"));
        params.put("properties", properties);
        return params;
    }

    @Override
    public ToolCall execute(Map<String, Object> args, Long userId) {
        String question = (String) args.getOrDefault("question", "");
        String knowledge = knowledgeBaseService.searchRelevant(question);

        return ToolCall.builder()
                .id(UUID.randomUUID().toString())
                .name(getName())
                .arguments(JSON.toJSONString(args))
                .result(knowledge != null ? knowledge : "知识库中暂无相关信息。")
                .build();
    }
}