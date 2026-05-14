package com.hongjie.pms.AI.tool;

import com.hongjie.pms.AI.modules.dto.ToolCall;

import java.util.Map;

public interface BaseTool {
    String getName();
    String getDescription();
    Map<String, Object> getParameters();
    ToolCall execute(Map<String, Object> args, Long userId);
}