package com.hongjie.pms.modules.search.dto;

import lombok.Data;
import java.util.List;

@Data
public class UnifiedSearchRequest {
    private String keyword;
    private Integer pageNum = 1;
    private Integer pageSize = 20;
    private List<String> types;  // daily, activity, pet
    private String sortBy;       // relevance, time, hot
    private Long userId;         // 当前用户ID，用于判断是否点赞/收藏
}