package com.hongjie.pms.modules.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.pojo.CommonResult;
import com.hongjie.pms.modules.admin.dto.request.BatchOperationRequest;
import com.hongjie.pms.modules.admin.dto.response.AdminUserSimpleDto;
import com.hongjie.pms.modules.admin.dto.response.BatchOperationResponse;
import com.hongjie.pms.modules.admin.service.AdminService;
import com.hongjie.pms.modules.petpost.dto.response.PetListResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * 管理员接口
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/pet-system/admin")
public class AdminController {

    private final AdminService adminService;

    /**
     * 审核通过
     */
    @PostMapping("/pet_post/accept")
    public CommonResult<String> accept(@RequestParam Long id) {
        Long userId = UserContext.getUserId();
        if (!UserContext.isAdmin()) {
            log.warn("非管理员用户尝试审核宠物信息: userId={}, petId={}", userId, id);
            throw new BusinessException(403, "无权操作，需要管理员权限");
        }
        adminService.accept(id);
        return CommonResult.success("审核通过");
    }

    /**
     * 审核拒绝
     */
    @PostMapping("/pet_post/reject")
    public CommonResult<String> reject(@RequestParam Long id, @RequestParam String reason) {
        Long userId = UserContext.getUserId();
        if (!UserContext.isAdmin()) {
            log.warn("非管理员用户尝试审核宠物信息: userId={}, petId={}", userId, id);
            throw new BusinessException(403, "无权操作，需要管理员权限");
        }
        adminService.reject(id, reason);
        return CommonResult.success("审核未通过");
    }

    /**
     * 用户列表（管理员）
     */
    @GetMapping("/users")
    public CommonResult<IPage<AdminUserSimpleDto>> userList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        log.info("管理员用户列表: pageNum={}, pageSize={}, keyword={}, status={}", pageNum, pageSize, keyword, status);
        IPage<AdminUserSimpleDto> page = adminService.userList(pageNum, pageSize, keyword, status);
        return CommonResult.success(page);
    }

    /**
     * 批量禁用用户
     */
    @PostMapping("/users/batch-disable")
    public CommonResult<BatchOperationResponse> batchDisableUsers(@RequestBody @Valid BatchOperationRequest request) {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        BatchOperationResponse response = adminService.batchDisableUsers(request.getUserIds());
        return CommonResult.success(response);
    }

    /**
     * 批量启用用户
     */
    @PostMapping("/users/batch-enable")
    public CommonResult<BatchOperationResponse> batchEnableUsers(@RequestBody @Valid BatchOperationRequest request) {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        BatchOperationResponse response = adminService.batchEnableUsers(request.getUserIds());
        return CommonResult.success(response);
    }

    /**
     * 批量重置用户密码
     */
    @PostMapping("/users/batch-reset-password")
    public CommonResult<BatchOperationResponse> batchResetPassword(@RequestBody @Valid BatchOperationRequest request) {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        BatchOperationResponse response = adminService.batchResetPassword(request.getUserIds());
        return CommonResult.success(response);
    }
}