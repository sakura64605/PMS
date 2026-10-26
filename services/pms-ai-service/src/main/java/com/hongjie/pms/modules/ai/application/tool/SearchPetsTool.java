package com.hongjie.pms.modules.ai.application.tool;
import com.hongjie.pms.ai.contract.BaseTool;
import com.hongjie.pms.modules.ai.api.dto.ToolCall;
import com.hongjie.pms.aiservice.core.AiCoreClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
@RequiredArgsConstructor
public class SearchPetsTool implements BaseTool {
    private final AiCoreClient core;
    @Override
    public String getName() {
        return "search_pets";
    }

    @Override
    public String getDescription() {
        return "搜索平台上的宠物领养/救助信息。注意：本工具是分页查询，每次只返回一页（默认5条）。"
                + "若结果里提示还有更多页，请用 page 参数递增继续获取下一页，不要凭当前一页就断定没有更多了。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        properties.put("keyword", Map.of("type", "string", "description", "搜索关键词，匹配标题/内容/宠物名"));
        properties.put("petType", Map.of("type", "string", "description", "具体品种名（自由文本），如 柴犬/金毛/橘猫"));
        properties.put("species", Map.of("type", "integer", "description",
                "物种分类：0猫 1狗 2兔 3啮齿类 4鸟类 5鱼类 6爬行/两栖 7其他。用户说'找狗/找猫/领养狗'等时填对应值"));
        properties.put("page", Map.of("type", "integer", "description", "页码，从1开始，默认1。翻下一页时 page+1 且其余参数保持一致"));
        properties.put("limit", Map.of("type", "integer", "description", "每页返回条数，默认5，最大20"));

        params.put("properties", properties);
        return params;
    }


    @Override public ToolCall execute(Map<String,Object> args, Long actor) {
        return core.tool(getName(),args,actor);
    }
}
