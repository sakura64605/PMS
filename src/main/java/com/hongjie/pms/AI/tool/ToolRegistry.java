package com.hongjie.pms.AI.tool;

import com.alibaba.fastjson2.JSON;
import com.hongjie.pms.AI.modules.dto.ToolCall;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
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

    /** 将所有工具转换为 langchain4j ToolSpecification 列表，供LLM function-calling使用 */
    public List<ToolSpecification> getToolSpecifications() {
        List<ToolSpecification> specs = new ArrayList<>();
        for (BaseTool tool : tools.values()) {
            specs.add(buildSpec(tool));
        }
        return specs;
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
        Map<String, Object> args = new HashMap<>();
        if (argumentsJson != null && !argumentsJson.isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = JSON.parseObject(argumentsJson, Map.class);
                args = parsed;
            } catch (Exception e) {
                log.warn("解析工具参数失败: {}", argumentsJson, e);
            }
        }
        return tool.execute(args, userId);
    }

    public Collection<BaseTool> getAllTools() {
        return tools.values();
    }

    private ToolSpecification buildSpec(BaseTool tool) {
        Map<String, Object> rawParams = tool.getParameters();
        ToolSpecification.Builder builder = ToolSpecification.builder()
                .name(tool.getName())
                .description(tool.getDescription());

        if (rawParams != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) rawParams.get("properties");
            if (properties != null) {
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    String propName = entry.getKey();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> propDef = (Map<String, Object>) entry.getValue();
                    String type = (String) propDef.get("type");
                    String desc = (String) propDef.get("description");

                    List<JsonSchemaProperty> props = new ArrayList<>();
                    if ("integer".equals(type)) {
                        props.add(JsonSchemaProperty.INTEGER);
                    } else if ("number".equals(type)) {
                        props.add(JsonSchemaProperty.NUMBER);
                    } else if ("boolean".equals(type)) {
                        props.add(JsonSchemaProperty.BOOLEAN);
                    } else {
                        props.add(JsonSchemaProperty.STRING);
                    }
                    if (desc != null) {
                        props.add(JsonSchemaProperty.description(desc));
                    }
                    // 所有参数都是可选的（工具内部有默认值处理）
                    builder.addOptionalParameter(propName, props);
                }
            }
        }

        return builder.build();
    }
}