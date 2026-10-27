package com.hongjie.pms.modules.search.application;

import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import java.util.*;

public record SearchInput(
        String keyword, int pageNum, int pageSize, List<String> types, String sortBy, Long actor) {
    public static SearchInput of(
            String keyword,
            Integer page,
            Integer size,
            List<String> types,
            String sort,
            Long actor) {
        int p = page == null ? 1 : page, s = size == null ? 20 : size;
        if (p < 1 || s < 1 || s > 100 || (long) p * s > 10000) bad();
        String text = keyword == null ? "" : keyword.strip();
        if (text.length() > 200) bad();
        String order = sort == null ? "relevance" : sort;
        if (!Set.of("relevance", "time", "hot").contains(order)) bad();
        var selected = types == null ? List.<String>of() : types.stream().distinct().toList();
        if (selected.stream()
                .anyMatch(t -> t == null || !Set.of("daily", "activity", "pet").contains(t))) bad();
        return new SearchInput(text, p, s, List.copyOf(selected), order, actor);
    }

    private static void bad() {
        throw new BusinessException(ErrorCode.PARAM_ERROR, "搜索参数无效");
    }
}
