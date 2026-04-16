package com.hongjie.pms.modules.audit.dto;

import com.hongjie.pms.modules.user.dto.UserSimpleDto;
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