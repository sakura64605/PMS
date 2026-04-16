package com.hongjie.pms.modules.audit.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.modules.audit.entity.AuditRecord;

import java.util.List;

public interface AuditService {

    void submit(String targetType, Long targetId);

    void approve(String targetType, Long targetId);

    void reject(String targetType, Long targetId, String reason);

    IPage<AuditRecord> getPendingList(String targetType, Integer pageNum, Integer pageSize);

    IPage<AuditRecord> getHistoryList(String targetType, Long targetId, Integer pageNum, Integer pageSize);
}
