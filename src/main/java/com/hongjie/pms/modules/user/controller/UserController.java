package com.hongjie.pms.modules.user.controller;

import com.hongjie.pms.common.pojo.CommonResult;
import com.hongjie.pms.modules.user.dto.UserSimpleDto;
import com.hongjie.pms.modules.user.dto.UserProfileDto;
import com.hongjie.pms.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/pet-system/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 搜索用户（按用户名或昵称）
     * @param keyword 搜索关键词
     * @return 用户列表
     */
    @GetMapping("/search")
    public CommonResult<List<UserSimpleDto>> searchUsers(@RequestParam String keyword) {
        log.info("搜索用户：{}", keyword);
        List<UserSimpleDto> users = userService.searchUsers(keyword);
        return CommonResult.success(users);
    }

    /**
     * 获取用户信息
     * @param userId 用户ID
     * @return 用户信息
     */
    @GetMapping("/profileInfo")
    public CommonResult<UserProfileDto> getUserInfo(@RequestParam Long userId) {
        log.info("查看用户{}信息", userId);
        UserProfileDto user = userService.getUserProfileInfo(userId);
        return CommonResult.success(user);
    }
}
