package com.hongjie.pms.modules.governance.audit.application;

/** status 为 null 表示仅提交申请，仅需清缓存。 */
public record AuditChanged(
        String type,
        Long targetId,
        Integer status,
        String reason,
        AuditWriteTarget.Snapshot target) {}
