package com.hongjie.pms.modules.ai.application.tool;

import com.hongjie.pms.modules.ai.api.dto.ToolCall;

import java.util.Map;

public interface BaseTool {
    String getName();
    String getDescription();
    Map<String, Object> getParameters();
    ToolCall execute(Map<String, Object> args, Long userId);
}
