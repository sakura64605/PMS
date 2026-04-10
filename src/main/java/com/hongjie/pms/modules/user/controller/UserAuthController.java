package com.hongjie.pms.modules.user.controller;

import com.hongjie.pms.common.annotation.RateLimit;
import com.hongjie.pms.common.base.BaseController;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.CommonResult;
import com.hongjie.pms.modules.user.dto.request.ChangePasswordRequestDto;
import com.hongjie.pms.modules.user.dto.request.LoginRequestDto;
import com.hongjie.pms.modules.user.dto.request.RegisterRequestDto;
import com.hongjie.pms.modules.user.dto.request.UserUpdateRequestDto;
import com.hongjie.pms.modules.user.dto.response.LoginResponseDto;
import com.hongjie.pms.modules.user.dto.response.RegisterResponseDto;
import com.hongjie.pms.modules.user.dto.response.UserInfoDto;
import com.hongjie.pms.modules.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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

    /**
     * 登录
     */
    @RateLimit(key = "login", count = 5, timeUnit = TimeUnit.MINUTES)
    @PostMapping("/login")
    public CommonResult<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto loginRequestDto) {
        LoginResponseDto response = userService.login(loginRequestDto);
        String message = "登录成功";
        return success(response, message);
    }

    /**
     * 注册
     */
    @RateLimit(
            key = "#request.phone",           // 按手机号限流
            count = 1,
            duration = 1,
            timeUnit = TimeUnit.DAYS,
            perUser = false,
            message = "该手机号已注册过，如需帮助请联系客服"
    )
    @PostMapping("/register")
    public CommonResult<RegisterResponseDto> register(@Valid @RequestBody RegisterRequestDto registerRequestDto) {
        RegisterResponseDto response = userService.register(registerRequestDto);
        String message = "注册成功";
        return success(response, message);
    }

    /**
     * 修改密码
     */
    @RateLimit(key = "changePassword", count = 5, timeUnit = TimeUnit.DAYS)
    @PostMapping("/changePassword")
    public CommonResult<String> updatePassword(@Valid @RequestBody ChangePasswordRequestDto changePasswordRequestDto) {
        Long userId = UserContext.getUserId();
        log.info("用户{}修改密码: {}", userId, changePasswordRequestDto);

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
    public CommonResult<UserInfoDto> updateUserInfo(@Valid @RequestBody UserUpdateRequestDto updateDto) {
        Long userId = UserContext.getUserId();
        log.info("用户{}更新信息: {}", userId, updateDto);

        UserInfoDto updatedInfo = userService.updateUserInfo(userId, updateDto);
        if(updatedInfo == null){
            return CommonResult.error(500, "更新失败");
        }
        return CommonResult.success(updatedInfo, "更新成功");
    }

}
