package com.hongjie.pms.modules.report.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ReportListDto {

    private Long id;

    // 目标类型
    private String targetType;

    // 目标类型名称
    private String targetTypeName;

    // 描述
    private String targetTypeDesc;

    // 目标ID
    private Long targetId;

    // 标题
    private String targetTitle;

    // 创建人
    private String reporterName;

    // 原因
    private String reason;

    // 状态
    private Integer status;

    // 状态描述
    private String statusDesc;

    // 处理结果
    private String handleResult;

    // 处理人
    private String handlerName;
    private LocalDateTime createTime;
    private LocalDateTime handleTime;
}