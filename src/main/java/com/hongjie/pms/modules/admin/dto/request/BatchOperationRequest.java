package com.hongjie.pms.modules.admin.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BatchOperationRequest {
    
    @NotEmpty(message = "用户ID列表不能为空")
    private List<Long> userIds;
}