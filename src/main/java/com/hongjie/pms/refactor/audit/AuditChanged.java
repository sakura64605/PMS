package com.hongjie.pms.refactor.audit;

/** status 为 null 表示仅提交申请，仅需清缓存。 */
public record AuditChanged(String type,Long targetId,Integer status,String reason,AuditWriteTarget.Snapshot target) {}
