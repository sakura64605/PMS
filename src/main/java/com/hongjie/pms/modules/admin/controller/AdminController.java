package com.hongjie.pms.modules.admin.controller;

import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.pojo.CommonResult;
import com.hongjie.pms.modules.admin.service.AdminService;
import com.hongjie.pms.modules.petpost.dto.response.PetListResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

}
