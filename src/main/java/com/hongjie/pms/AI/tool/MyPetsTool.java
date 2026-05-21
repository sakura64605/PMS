package com.hongjie.pms.AI.tool;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.AI.modules.dto.ToolCall;
import com.hongjie.pms.modules.petpost.entity.PetPost;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class MyPetsTool implements BaseTool {

    private final PetPostMapper petPostMapper;

    @Override
    public String getName() { return "my_pets"; }

    @Override
    public String getDescription() { return "查询当前用户发布的宠物领养/救助帖子"; }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        properties.put("status", Map.of("type", "string", "description", "帖子状态: published-已发布, adopted-已领养, offline-已下架, 不传则查全部"));
        properties.put("limit", Map.of("type", "integer", "description", "返回数量，默认5"));
        params.put("properties", properties);
        return params;
    }

    @Override
    public ToolCall execute(Map<String, Object> args, Long userId) {
        String statusFilter = (String) args.get("status");
        int limit = args.containsKey("limit") ? ((Number) args.get("limit")).intValue() : 5;

        LambdaQueryWrapper<PetPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PetPost::getUserId, userId);

        if ("published".equals(statusFilter)) {
            wrapper.eq(PetPost::getStatus, 1).eq(PetPost::getAuditStatus, 1);
        } else if ("adopted".equals(statusFilter)) {
            wrapper.eq(PetPost::getStatus, 2);
        } else if ("offline".equals(statusFilter)) {
            wrapper.eq(PetPost::getStatus, 3);
        }
        wrapper.orderByDesc(PetPost::getCreateTime).last("LIMIT " + limit);

        List<PetPost> pets = petPostMapper.selectList(wrapper);

        StringBuilder result = new StringBuilder();
        if (pets.isEmpty()) {
            result.append("您还没有发布过宠物领养/救助帖子。");
        } else {
            result.append("您共发布了相关帖子，最近").append(pets.size()).append("条：\n\n");
            for (int i = 0; i < pets.size(); i++) {
                PetPost pet = pets.get(i);
                String statusText = switch (pet.getStatus()) {
                    case 1 -> "已发布";
                    case 2 -> "已领养/完成";
                    case 3 -> "已下架";
                    default -> "未知";
                };
                result.append(i + 1).append(". 【").append(pet.getTitle()).append("】").append(statusText).append("\n");
                if (pet.getPetName() != null) result.append("   宠物名：").append(pet.getPetName()).append("\n");
                if (pet.getPetType() != null) result.append("   品种：").append(pet.getPetType()).append("\n");
                result.append("   浏览：").append(pet.getViewCount()).append(" 点赞：").append(pet.getLikeCount()).append("\n\n");
            }
        }

        return ToolCall.builder()
                .id(UUID.randomUUID().toString())
                .name(getName())
                .arguments(JSON.toJSONString(args))
                .result(result.toString())
                .build();
    }
}