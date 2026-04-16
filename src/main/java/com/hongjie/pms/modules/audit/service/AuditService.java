package com.hongjie.pms.modules.audit.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.modules.audit.dto.AuditHistoryDto;
import com.hongjie.pms.modules.audit.dto.AuditListDto;

import java.util.List;

public interface AuditService {

    /**
     * 提交审核
     */
    void submit(String targetType, Long targetId);

    /**
     * 单个通过
     */
    void approve(Long id, String targetType);

    /**
     * 单个拒绝
     */
    void reject(Long id, String targetType, String reason);

    /**
     * 批量通过
     */
    void batchApprove(List<Long> ids, String targetType);

    /**
     * 批量拒绝
     */
    void batchReject(List<Long> ids, String targetType, String reason);

    /**
     * 待审核列表
     */
    IPage<AuditListDto> getPendingList(String targetType, String keyword,
                                       String dateRange, Integer pageNum, Integer pageSize);

    /**
     * 审核历史
     */
    IPage<AuditHistoryDto> getHistoryList(String targetType, String keyword,
                                          Integer auditStatus, Integer pageNum, Integer pageSize);

    /**
     * 获取详情
     */
    Object getDetail(Long id, String targetType);
}