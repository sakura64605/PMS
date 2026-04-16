package com.hongjie.pms.modules.audit.dto;

import com.hongjie.pms.modules.user.dto.UserSimpleDto;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AuditListDto {

    private Long id;
    private String targetType;      // adopt / help / activity
    private String targetTypeDesc;  // 领养 / 救助 / 活动
    private String title;
    private String content;
    private List<String> images;
    private UserSimpleDto user;
    private LocalDateTime createTime;
    private Integer auditStatus;
    private String auditStatusDesc;

    // 宠物专用
    private String petType;
    private String petName;
    private String petAge;
    private Integer petGender;
    private String address;
    private String contactPhone;

    // 活动专用
    private String location;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer maxPeople;
    private Integer currentPeople;
}