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
    public String getDescription() {
        return "查询当前用户发布的宠物领养/救助帖子。注意：分页查询，每次只返回一页（默认5条），"
                + "若提示还有更多请用 page 递增继续。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        properties.put("status", Map.of("type", "string", "description", "帖子状态: published-已发布, adopted-已领养, offline-已下架, 不传则查全部"));
        properties.put("page", Map.of("type", "integer", "description", "页码，从1开始，默认1"));
        properties.put("limit", Map.of("type", "integer", "description", "每页条数，默认5，最大20"));
        params.put("properties", properties);
        return params;
    }

    @Override
    public ToolCall execute(Map<String, Object> args, Long userId) {
        String statusFilter = (String) args.get("status");
        int page = ToolPaging.page(args.get("page"));
        int pageSize = ToolPaging.pageSize(args.get("limit"));
        int offset = (page - 1) * pageSize;

        LambdaQueryWrapper<PetPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PetPost::getUserId, userId);
        if ("published".equals(statusFilter)) {
            wrapper.eq(PetPost::getStatus, 1).eq(PetPost::getAuditStatus, 1);
        } else if ("adopted".equals(statusFilter)) {
            wrapper.eq(PetPost::getStatus, 2);
        } else if ("offline".equals(statusFilter)) {
            wrapper.eq(PetPost::getStatus, 3);
        }
        wrapper.orderByDesc(PetPost::getCreateTime);

        long total = petPostMapper.selectCount(wrapper);
        List<PetPost> pets = petPostMapper.selectList(
                wrapper.last("LIMIT " + offset + ", " + pageSize));
        int totalPages = total == 0 ? 0 : (int) ((total + pageSize - 1) / pageSize);

        StringBuilder result = new StringBuilder();
        if (pets.isEmpty()) {
            if (total == 0) {
                result.append("您还没有发布过宠物领养/救助帖子。");
            } else {
                result.append("第 ").append(page).append(" 页没有更多帖子了（共 ").append(total).append(" 条，已全部展示）。");
            }
        } else {
            result.append("您共发布 ").append(total).append(" 条相关帖子");
            if (totalPages > 1) result.append("（共 ").append(totalPages).append(" 页）");
            result.append("，当前第 ").append(page).append(" 页：\n\n");

            for (int i = 0; i < pets.size(); i++) {
                PetPost pet = pets.get(i);
                int no = offset + i + 1;
                String statusText = switch (pet.getStatus()) {
                    case 1 -> "已发布";
                    case 2 -> "已领养/完成";
                    case 3 -> "已下架";
                    default -> "未知";
                };
                result.append(no).append(". 【").append(pet.getTitle()).append("】").append(statusText).append("\n");
                if (pet.getPetName() != null) result.append("   宠物名：").append(pet.getPetName()).append("\n");
                if (pet.getPetType() != null) result.append("   品种：").append(pet.getPetType()).append("\n");
                result.append("   浏览：").append(pet.getViewCount()).append(" 点赞：").append(pet.getLikeCount()).append("\n\n");
            }
            ToolPaging.appendPagingFooter(result, page, totalPages, total, pageSize, "条");
        }

        return ToolCall.builder()
                .id(UUID.randomUUID().toString())
                .name(getName())
                .arguments(JSON.toJSONString(args))
                .result(result.toString())
                .build();
    }
}
