package com.hongjie.pms.AI.tool;

import com.alibaba.fastjson2.JSON;
import com.hongjie.pms.AI.modules.dto.ToolCall;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, BaseTool> tools = new ConcurrentHashMap<>();

    public ToolRegistry(List<BaseTool> toolList) {
        for (BaseTool tool : toolList) {
            tools.put(tool.getName(), tool);
        }
        log.info("已注册工具: {}", tools.keySet());
    }

    /** 将所有工具转换为 langchain4j ToolSpecification 列表，供 LLM function-calling 使用 */
    public List<ToolSpecification> getToolSpecifications() {
        List<ToolSpecification> specs = new ArrayList<>();
        for (BaseTool tool : tools.values()) {
            specs.add(buildSpec(tool));
        }
        return specs;
    }

    /**
     * 将工具注册为 langchain4j 1.x 的 ToolSpecification + ToolExecutor 映射，供 Agent 使用。
     * 闭包捕获 userId，工具执行异常时把错误信息作为观察结果回喂给模型（便于 ReAct 循环自行纠错）。
     */
    public Map<ToolSpecification, ToolExecutor> getToolExecutors(Long userId) {
        Map<ToolSpecification, ToolExecutor> executors = new HashMap<>();
        for (BaseTool tool : tools.values()) {
            ToolSpecification spec = buildSpec(tool);
            executors.put(spec, (request, memoryId) -> {
                try {
                    ToolCall call = tool.execute(parseArguments(request.arguments()), userId);
                    return call.getResult() != null ? call.getResult() : "工具执行成功";
                } catch (Exception e) {
                    log.warn("工具 {} 执行异常: {}", tool.getName(), e.getMessage(), e);
                    return "工具执行出错: " + e.getMessage();
                }
            });
        }
        return executors;
    }

    /** 根据LLM返回的工具名和参数执行工具 */
    public ToolCall execute(String toolName, String argumentsJson, Long userId) {
        BaseTool tool = tools.get(toolName);
        if (tool == null) {
            log.warn("未找到工具: {}", toolName);
            return ToolCall.builder()
                    .name(toolName)
                    .result("工具不存在: " + toolName)
                    .build();
        }
        return tool.execute(parseArguments(argumentsJson), userId);
    }

    public Collection<BaseTool> getAllTools() {
        return tools.values();
    }

    private Map<String, Object> parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isEmpty()) {
            return new HashMap<>();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = JSON.parseObject(argumentsJson, Map.class);
            return parsed != null ? parsed : new HashMap<>();
        } catch (Exception e) {
            log.warn("解析工具参数失败: {}", argumentsJson, e);
            return new HashMap<>();
        }
    }

    private ToolSpecification buildSpec(BaseTool tool) {
        Map<String, Object> rawParams = tool.getParameters();
        ToolSpecification.Builder builder = ToolSpecification.builder()
                .name(tool.getName())
                .description(tool.getDescription());

        if (rawParams != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) rawParams.get("properties");
            if (properties != null && !properties.isEmpty()) {
                JsonObjectSchema.Builder schemaBuilder = JsonObjectSchema.builder();
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    String propName = entry.getKey();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> propDef = (Map<String, Object>) entry.getValue();
                    String type = propDef != null ? (String) propDef.get("type") : null;
                    String desc = propDef != null ? (String) propDef.get("description") : null;

                    if ("integer".equals(type)) {
                        schemaBuilder.addIntegerProperty(propName, desc);
                    } else if ("number".equals(type)) {
                        schemaBuilder.addNumberProperty(propName, desc);
                    } else if ("boolean".equals(type)) {
                        schemaBuilder.addBooleanProperty(propName, desc);
                    } else {
                        schemaBuilder.addStringProperty(propName, desc);
                    }
                }
                // 所有参数均不 required —— 工具内部有默认值处理
                builder.parameters(schemaBuilder.build());
            }
        }

        return builder.build();
    }
}
