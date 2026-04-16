package com.hongjie.pms.modules.audit.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("audit_record")
public class AuditRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String targetType;      // pet / activity
    private Long targetId;          // 目标ID
    private Integer auditStatus;    // 0-待审核 1-审核通过 2-审核拒绝
    private String rejectReason;    // 拒绝原因
    private Long auditorId;         // 审核人ID
    private LocalDateTime auditTime;// 审核时间

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}