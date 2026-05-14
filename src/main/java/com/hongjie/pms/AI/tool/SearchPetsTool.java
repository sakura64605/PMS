package com.hongjie.pms.AI.tool;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.AI.modules.dto.ToolCall;
import com.hongjie.pms.modules.petpost.entity.PetPost;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchPetsTool implements BaseTool {

    private final PetPostMapper petPostMapper;

    @Override
    public String getName() {
        return "search_pets";
    }

    @Override
    public String getDescription() {
        return "搜索平台上的宠物领养/救助信息";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        properties.put("keyword", Map.of("type", "string", "description", "搜索关键词"));
        properties.put("petType", Map.of("type", "string", "description", "宠物品种"));
        properties.put("limit", Map.of("type", "integer", "description", "返回数量，默认5"));

        params.put("properties", properties);
        return params;
    }

    @Override
    public ToolCall execute(Map<String, Object> args, Long userId) {
        String keyword = (String) args.getOrDefault("keyword", "");
        String petType = (String) args.get("petType");
        String title = (String) args.get("title");
        String content = (String) args.get("content");
        int limit = args.containsKey("limit") ? (int) args.get("limit") : 5;

        log.info("搜索宠物: keyword={}, petType={}, limit={}", keyword, petType, limit);

        LambdaQueryWrapper<PetPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PetPost::getStatus, 1)
                .eq(PetPost::getAuditStatus, 1)
                .orderByDesc(PetPost::getCreateTime);

//        if (petType != null && !petType.isEmpty()) {
//            wrapper.like(PetPost::getPetType, petType);
//        }
        if (title != null && !title.isEmpty()) {
            wrapper.like(PetPost::getTitle, title);
        }
        if (content != null && !content.isEmpty()) {
            wrapper.like(PetPost::getContent, content);
        }
        wrapper.last("LIMIT " + limit);

        List<PetPost> pets = petPostMapper.selectList(wrapper);

        log.info("搜索到 {} 条宠物信息", pets.size());

        // 构建详细的结果文本，让 AI 能够理解
        StringBuilder result = new StringBuilder();

        if (pets.isEmpty()) {
            result.append("没有找到符合条件的宠物信息。");
            if (keyword != null && !keyword.isEmpty()) {
                result.append(" 建议尝试其他关键词，比如'猫'、'狗'或具体品种名。");
            }
        } else {
            result.append("找到 ").append(pets.size()).append(" 只等待领养/救助的宠物：\n\n");
            for (int i = 0; i < pets.size(); i++) {
                PetPost pet = pets.get(i);
                result.append(i + 1).append(". 【").append(pet.getPetName() != null ? pet.getPetName() : "无名").append("】\n");
                result.append("   品种：").append(pet.getPetType() != null ? pet.getPetType() : "未知").append("\n");
                result.append("   年龄：").append(pet.getPetAge() != null ? pet.getPetAge() : "未知").append("\n");
                result.append("   性别：").append(pet.getPetGender() != null ? (pet.getPetGender() == 1 ? "公" : "母") : "未知").append("\n");
                result.append("   地点：").append(pet.getAddress() != null ? pet.getAddress() : "未知").append("\n");
                result.append("   标题：").append(pet.getTitle()).append("\n");
                result.append("   描述：").append(pet.getContent() != null && pet.getContent().length() > 100 ?
                        pet.getContent().substring(0, 100) + "..." : pet.getContent()).append("\n\n");
            }
            result.append("用户如果想了解某一只的详细信息，可以告诉我宠物名称。");
        }

        log.info("返回结果长度: {} 字符", result.length());

        return ToolCall.builder()
                .id(UUID.randomUUID().toString())
                .name(getName())
                .arguments(JSON.toJSONString(args))
                .result(result.toString())
                .build();
    }
}