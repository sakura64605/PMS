package com.hongjie.pms.modules.audit.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.pojo.CommonResult;
import com.hongjie.pms.modules.audit.dto.AuditHistoryDto;
import com.hongjie.pms.modules.audit.dto.AuditListDto;
import com.hongjie.pms.modules.audit.dto.BatchAuditRequest;
import com.hongjie.pms.modules.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/pet-system/admin/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    private void checkAdmin() {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(403, "无权操作，需要管理员权限");
        }
    }

    /**
     * 待审核列表
     */
    @GetMapping("/pending")
    public CommonResult<IPage<AuditListDto>> getPendingList(
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String dateRange,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        checkAdmin();
        return CommonResult.success(auditService.getPendingList(targetType, keyword, dateRange, pageNum, pageSize));
    }

    /**
     * 审核历史
     */
    @GetMapping("/history")
    public CommonResult<IPage<AuditHistoryDto>> getHistoryList(
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer auditStatus,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        checkAdmin();
        return CommonResult.success(auditService.getHistoryList(targetType, keyword, auditStatus, pageNum, pageSize));
    }

    /**
     * 获取详情
     */
    @GetMapping("/detail")
    public CommonResult<Object> getDetail(@RequestParam String targetType, @RequestParam Long id) {
        checkAdmin();
        return CommonResult.success(auditService.getDetail(id, targetType));
    }

    /**
     * 批量通过
     */
    @PostMapping("/batch-approve")
    public CommonResult<String> batchApprove(@RequestParam String targetType, @RequestBody BatchAuditRequest request) {
        checkAdmin();
        auditService.batchApprove(request.getIds(), targetType);
        return CommonResult.success("批量审核通过");
    }

    /**
     * 批量拒绝
     */
    @PostMapping("/batch-reject")
    public CommonResult<String> batchReject(@RequestParam String targetType, @Valid @RequestBody BatchAuditRequest request) {
        checkAdmin();
        auditService.batchReject(request.getIds(), targetType, request.getRejectReason());
        return CommonResult.success("批量审核拒绝");
    }
}