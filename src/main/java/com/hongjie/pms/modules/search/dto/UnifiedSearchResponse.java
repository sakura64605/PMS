package com.hongjie.pms.modules.search.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UnifiedSearchResponse {
    private Long total;
    private Integer pageNum;
    private Integer pageSize;
    private Integer totalPages;
    private List<SearchResultItem> items;
}