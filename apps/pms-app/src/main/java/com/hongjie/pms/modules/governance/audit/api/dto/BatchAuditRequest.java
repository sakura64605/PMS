package com.hongjie.pms.modules.governance.audit.api.dto;

import lombok.Data;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
public class BatchAuditRequest {

    @NotEmpty(message = "ID列表不能为空")
    private List<Long> ids;

    private String rejectReason;
}
