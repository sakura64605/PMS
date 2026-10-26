package com.hongjie.pms.ai.contract;
import com.hongjie.pms.modules.ai.api.dto.ToolCall;
import java.util.Map;
/** Narrow port for business-owned, read-only AI queries. */
public interface CoreQueryTool {
    String getName();
    ToolCall execute(Map<String,Object> arguments, Long actor);
}
