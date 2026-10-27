package com.hongjie.pms.ai.contract;

import com.hongjie.pms.modules.ai.api.dto.ToolCall;

import java.util.Map;

public interface BaseTool extends CoreQueryTool {
    String getName();

    String getDescription();

    Map<String, Object> getParameters();

    ToolCall execute(Map<String, Object> args, Long userId);
}
