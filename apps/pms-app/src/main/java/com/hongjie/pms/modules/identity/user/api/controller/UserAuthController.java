package com.hongjie.pms.modules.identity.user.api.controller;

import com.hongjie.pms.platform.annotation.RedisRateLimit;
import com.hongjie.pms.platform.base.BaseController;
import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.pojo.CommonResult;
import com.hongjie.pms.modules.identity.user.api.dto.request.ChangePasswordRequestDto;
import com.hongjie.pms.modules.identity.user.api.dto.request.LoginRequestDto;
import com.hongjie.pms.modules.identity.user.api.dto.request.RegisterRequestDto;
import com.hongjie.pms.modules.identity.user.api.dto.request.UserUpdateRequestDto;
import com.hongjie.pms.modules.identity.user.api.dto.response.LoginResponseDto;
import com.hongjie.pms.modules.identity.user.api.dto.response.RegisterResponseDto;
import com.hongjie.pms.modules.identity.user.api.dto.response.UserInfoDto;
import com.hongjie.pms.modules.identity.user.api.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import com.hongjie.pms.platform.utils.JWTUtils;
import jakarta.servlet.http.HttpServletRequest;

import java.util.concurrent.TimeUnit;

/**
 * 用户认证控制器
 * @author: Hongjie
 * @date: 2026/04/01
 **/
@Slf4j
@RestController
@RequestMapping("/pet-system/user")
@RequiredArgsConstructor
public class UserAuthController extends BaseController {

    private final UserService userService;
    private final JWTUtils jwtUtils;
    private final com.hongjie.pms.modules.identity.user.application.UserSessions sessions;

    /**
     * 登录
     */
    @RedisRateLimit(
            key = "login",
            capacity = 5,
            refillRate = 5,
            duration = 1,
            timeUnit = TimeUnit.MINUTES)
    @PostMapping("/login")
    public CommonResult<LoginResponseDto> login(
            @Valid @RequestBody LoginRequestDto loginRequestDto) {
        LoginResponseDto response = userService.login(loginRequestDto);
        String message = "登录成功";
        return success(response, message);
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public CommonResult<String> logout(HttpServletRequest request) {
        String header = request.getHeader(jwtUtils.getHeader());
        sessions.logout(header);
        log.info("用户退出登录, userId={}", UserContext.getUserId());
        return CommonResult.success("退出登录成功");
    }

    /**
     * 注册
     */
    @RedisRateLimit(
            key = "#registerRequestDto.phone", // 按手机号限流
            capacity = 1,
            refillRate = 1,
            duration = 1,
            timeUnit = TimeUnit.DAYS,
            perUser = false,
            message = "该手机号已注册过，如需帮助请联系客服")
    @PostMapping("/register")
    public CommonResult<RegisterResponseDto> register(
            @Valid @RequestBody RegisterRequestDto registerRequestDto) {
        RegisterResponseDto response = userService.register(registerRequestDto);
        String message = "注册成功";
        return success(response, message);
    }

    /**
     * 修改密码
     */
    @RedisRateLimit(
            key = "changePassword",
            capacity = 5,
            refillRate = 5,
            duration = 1,
            timeUnit = TimeUnit.DAYS)
    @PostMapping("/changePassword")
    public CommonResult<String> updatePassword(
            @Valid @RequestBody ChangePasswordRequestDto changePasswordRequestDto) {
        Long userId = UserContext.getUserId();

        userService.changePassword(userId, changePasswordRequestDto);
        return CommonResult.success("修改密码成功");
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/info")
    public CommonResult<UserInfoDto> info() {
        UserInfoDto response = userService.getUserInfo();
        String message = "获取用户信息成功";
        return success(response, message);
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/update")
    public CommonResult<UserInfoDto> updateUserInfo(
            @Valid @RequestBody UserUpdateRequestDto updateDto) {
        Long userId = UserContext.getUserId();
        log.info("User profile update: userId={}", userId);

        UserInfoDto updatedInfo = userService.updateUserInfo(userId, updateDto);
        if (updatedInfo == null) {
            return CommonResult.error(500, "更新失败");
        }
        return CommonResult.success(updatedInfo, "更新成功");
    }
}
