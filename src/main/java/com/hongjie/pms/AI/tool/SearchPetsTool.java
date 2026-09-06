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

    private static final int DEFAULT_PAGE_SIZE = 5;
    private static final int MAX_PAGE_SIZE = 20;

    private final PetPostMapper petPostMapper;

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

    @Override
    public ToolCall execute(Map<String, Object> args, Long userId) {
        String keyword = (String) args.getOrDefault("keyword", "");
        String petType = (String) args.get("petType");
        Integer species = args.get("species") instanceof Number
                ? ((Number) args.get("species")).intValue() : null;
        int page = args.containsKey("page") ? ((Number) args.get("page")).intValue() : 1;
        if (page < 1) {
            page = 1;
        }
        int pageSize = args.containsKey("limit") ? ((Number) args.get("limit")).intValue() : DEFAULT_PAGE_SIZE;
        if (pageSize < 1) {
            pageSize = DEFAULT_PAGE_SIZE;
        }
        if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        }
        int offset = (page - 1) * pageSize;

        log.info("搜索宠物: keyword={}, petType={}, species={}, page={}, limit={}",
                keyword, petType, species, page, pageSize);

        // 先统计总条数（无分页）
        long total = petPostMapper.selectCount(buildWrapper(keyword, petType, species, false));
        // 再取当前页
        List<PetPost> pets = petPostMapper.selectList(
                buildWrapper(keyword, petType, species, true).last("LIMIT " + offset + ", " + pageSize));

        log.info("搜索到宠物: 共 {} 条，本页 {} 条", total, pets.size());

        int totalPages = total == 0 ? 0 : (int) ((total + pageSize - 1) / pageSize);

        StringBuilder result = new StringBuilder();

        if (pets.isEmpty()) {
            if (total == 0) {
                result.append("没有找到符合条件的宠物。");
                if (keyword != null && !keyword.isEmpty()) {
                    result.append(" 建议换关键词、放宽品种/物种条件再试。");
                }
            } else {
                result.append("第 ").append(page).append(" 页没有更多宠物了（共 ").append(total).append(" 只，已全部展示完毕）。");
            }
        } else {
            result.append("共找到 ").append(total).append(" 只符合条件的宠物");
            if (totalPages > 1) {
                result.append("（共 ").append(totalPages).append(" 页）");
            }
            result.append("，当前第 ").append(page).append(" 页，本页 ").append(pets.size()).append(" 只：\n\n");

            for (int i = 0; i < pets.size(); i++) {
                PetPost pet = pets.get(i);
                int no = offset + i + 1; // 全局编号，跨页连续
                result.append(no).append(". 【").append(pet.getPetName() != null ? pet.getPetName() : "无名").append("】\n");
                result.append("   品种：").append(pet.getPetType() != null ? pet.getPetType() : "未知").append("\n");
                result.append("   年龄：").append(pet.getPetAge() != null ? pet.getPetAge() : "未知").append("\n");
                result.append("   性别：").append(pet.getPetGender() != null ? (pet.getPetGender() == 1 ? "公" : "母") : "未知").append("\n");
                result.append("   地点：").append(pet.getAddress() != null ? pet.getAddress() : "未知").append("\n");
                result.append("   标题：").append(pet.getTitle()).append("\n");
                result.append("   描述：").append(pet.getContent() != null && pet.getContent().length() > 100 ?
                        pet.getContent().substring(0, 100) + "..." : pet.getContent()).append("\n\n");
            }

            if (page < totalPages) {
                long remaining = total - (long) page * pageSize;
                result.append("还有 ").append(Math.max(0, remaining))
                        .append(" 只未展示。若用户想看更多，请再次调用 search_pets 并传 page=").append(page + 1)
                        .append("（其余参数保持不变）。");
            } else {
                result.append("已展示全部 ").append(total).append(" 只。");
            }
            result.append(" 用户如果想了解某一只的详细信息，可以告诉我宠物名称。");
        }

        log.info("返回结果长度: {} 字符", result.length());

        return ToolCall.builder()
                .id(UUID.randomUUID().toString())
                .name(getName())
                .arguments(JSON.toJSONString(args))
                .result(result.toString())
                .build();
    }

    /** 构建筛选条件：物种精确 + 品种模糊 + 关键词(标题/内容/宠物名)，可带排序 */
    private LambdaQueryWrapper<PetPost> buildWrapper(String keyword, String petType, Integer species, boolean withOrder) {
        LambdaQueryWrapper<PetPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PetPost::getStatus, 1)
                .eq(PetPost::getAuditStatus, 1);

        if (species != null) {
            wrapper.eq(PetPost::getPetCategory, species);
        }
        if (petType != null && !petType.isEmpty()) {
            wrapper.like(PetPost::getPetType, petType);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(PetPost::getTitle, keyword)
                    .or()
                    .like(PetPost::getContent, keyword)
                    .or()
                    .like(PetPost::getPetName, keyword));
        }
        if (withOrder) {
            wrapper.orderByDesc(PetPost::getCreateTime);
        }
        return wrapper;
    }
}
