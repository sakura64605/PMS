package com.hongjie.pms.AI.tool;

import com.alibaba.fastjson2.JSON;
import com.hongjie.pms.AI.modules.dto.ToolCall;
import com.hongjie.pms.AI.modules.dto.request.AIAgentRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    
    public List<ToolCall> detectAndExecute(String response, AIAgentRequest request) {
        return new ArrayList<>();
    }
    
    public BaseTool getTool(String name) {
        return tools.get(name);
    }
}