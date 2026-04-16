package com.hongjie.pms.modules.audit.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.pojo.CommonResult;
import com.hongjie.pms.modules.audit.entity.AuditRecord;
import com.hongjie.pms.modules.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 审核
 */
@Slf4j
@RestController
@RequestMapping("/pet-system/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    /**
     * 提交审核
     */
    @PostMapping("/submit")
    public CommonResult<String> submit(@RequestParam String targetType, @RequestParam Long targetId) {
        checkAdmin();
        auditService.submit(targetType, targetId);
        return CommonResult.success("提交审核成功");
    }

    /**
     * 审核通过
     */
    @PostMapping("/approve")
    public CommonResult<String> approve(@RequestParam String targetType, @RequestParam Long targetId) {
        checkAdmin();
        auditService.approve(targetType, targetId);
        return CommonResult.success("审核通过");
    }

    /**
     * 审核拒绝
     */
    @PostMapping("/reject")
    public CommonResult<String> reject(@RequestParam String targetType, @RequestParam Long targetId, @RequestParam String reason) {
        checkAdmin();
        auditService.reject(targetType, targetId, reason);
        return CommonResult.success("审核拒绝");
    }

    /**
     * 待审核列表
     */
    @GetMapping("/pending")
    public CommonResult<IPage<AuditRecord>> getPendingList(
            @RequestParam String targetType,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        checkAdmin();
        return CommonResult.success(auditService.getPendingList(targetType, pageNum, pageSize));
    }

    /**
     * 审核历史列表
     */
    @GetMapping("/history")
    public CommonResult<IPage<AuditRecord>> getHistoryList(
            @RequestParam String targetType,
            @RequestParam Long targetId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        checkAdmin();
        return CommonResult.success(auditService.getHistoryList(targetType, targetId, pageNum, pageSize));
    }

    private void checkAdmin() {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(403, "无权操作，需要管理员权限");
        }
    }
}