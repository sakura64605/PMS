package com.hongjie.pms.modules.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.pojo.CommonResult;
import com.hongjie.pms.modules.admin.dto.response.AdminUserSimpleDto;
import com.hongjie.pms.modules.admin.service.AdminService;
import com.hongjie.pms.modules.petpost.dto.response.PetListResponseDto;
import com.hongjie.pms.modules.user.dto.UserSimpleDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员控制类
 * @author: HongJie
 * @date: 2020/5/23
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
    public CommonResult<String> accept(
            @RequestParam Long id) {

        // 1. 获取当前用户
        Long userId = UserContext.getUserId();

        // 2. 检查是否是管理员
        if (!UserContext.isAdmin()) {
            log.warn("非管理员用户尝试审核宠物信息: userId={}, petId={}", userId, id);
            throw new BusinessException(403, "无权操作，需要管理员权限");
        }

        PetListResponseDto response = adminService.accept(id);
        return CommonResult.success("审核通过");
    }

    /**
     * 审核拒绝
     */
    @PostMapping("/pet_post/reject")
    public CommonResult<String> reject(
            @RequestParam Long id) {

        // 1. 获取当前用户
        Long userId = UserContext.getUserId();

        // 2. 检查是否是管理员
        if (!UserContext.isAdmin()) {
            log.warn("非管理员用户尝试审核宠物信息: userId={}, petId={}", userId, id);
            throw new BusinessException(403, "无权操作，需要管理员权限");
        }

        PetListResponseDto response = adminService.reject(id);
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
     * 禁用用户
     */
    @PostMapping("/users/disable")
    public CommonResult<String> disable(
            @RequestParam Long userId) {

        // 1. 获取当前用户
        Long currentUserId = UserContext.getUserId();

        // 2. 检查是否是管理员
        if (!UserContext.isAdmin()) {
            log.warn("非管理员用户尝试禁用用户: userId={}, targetUserId={}", currentUserId, userId);
            throw new BusinessException(403, "无权操作，需要管理员权限");
        }

        // 3. 禁用用户
        adminService.disableUser(userId);
        return CommonResult.success("禁用成功");
    }

    /**
     * 启用用户
     */
    @PostMapping("/users/enable")
    public CommonResult<String> enable(
            @RequestParam Long userId) {
        // 1. 获取当前用户
        Long currentUserId = UserContext.getUserId();
        // 2. 检查是否是管理员
        if (!UserContext.isAdmin()) {
            log.warn("非管理员用户尝试启用用户: userId={}, targetUserId={}", currentUserId, userId);
            throw new BusinessException(403, "无权操作，需要管理员权限");
        }
        // 3. 启用用户
        adminService.enableUser(userId);
        return CommonResult.success("启用成功");
    }

}
