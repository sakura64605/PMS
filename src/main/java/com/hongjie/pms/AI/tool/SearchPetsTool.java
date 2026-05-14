package com.hongjie.pms.AI.tool;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.AI.modules.dto.ToolCall;
import com.hongjie.pms.modules.petpost.entity.PetPost;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
        int limit = args.containsKey("limit") ? (int) args.get("limit") : 5;
        
        LambdaQueryWrapper<PetPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PetPost::getStatus, 1);
        wrapper.eq(PetPost::getAuditStatus, 1);
        
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(PetPost::getTitle, keyword).or().like(PetPost::getContent, keyword));
        }
        if (petType != null && !petType.isEmpty()) {
            wrapper.like(PetPost::getPetType, petType);
        }
        wrapper.last("LIMIT " + limit);
        
        List<PetPost> pets = petPostMapper.selectList(wrapper);
        
        return ToolCall.builder()
                .id(UUID.randomUUID().toString())
                .name(getName())
                .arguments(JSON.toJSONString(args))
                .result("找到 " + pets.size() + " 条宠物信息")
                .build();
    }
}