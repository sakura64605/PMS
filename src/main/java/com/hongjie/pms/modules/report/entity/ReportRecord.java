package com.hongjie.pms.modules.report.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("report_record")
public class ReportRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 举报人id
    private Long reporterId;

    // 目标类型
    private String targetType;

    // 目标id
    private Long targetId;

    // 举报原因
    private String reason;

    // 0:待处理 1:处理中 2:处理完成
    private Integer status;

    // 处理人id
    private Long handlerId;

    // 处理结果
    private String handleResult;

    // 处理时间
    private LocalDateTime handleTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}