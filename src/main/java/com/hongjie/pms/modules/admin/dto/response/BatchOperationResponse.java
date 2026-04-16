package com.hongjie.pms.modules.admin.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BatchOperationResponse {
    
    private int totalCount;          // 总数
    private int successCount;        // 成功数
    private int failCount;           // 失败数
    private List<FailResult> failList;  // 失败列表
    
    @Data
    @Builder
    public static class FailResult {
        private Long id;             // 用户ID
        private String reason;       // 失败原因
    }
}