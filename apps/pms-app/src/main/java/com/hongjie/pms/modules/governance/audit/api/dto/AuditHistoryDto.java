package com.hongjie.pms.modules.governance.audit.api.dto;

import com.hongjie.pms.modules.identity.user.api.dto.UserSimpleDto;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class AuditHistoryDto {

    private Long id;
    private String targetType;
    private String targetTypeDesc;
    private Long targetId;
    private String title;
    private UserSimpleDto user;
    private Integer auditStatus;
    private String auditStatusDesc;
    private String rejectReason;
    private String auditorName;
    private LocalDateTime auditTime;
    private LocalDateTime createTime;
}