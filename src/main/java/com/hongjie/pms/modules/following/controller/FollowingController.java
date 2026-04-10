package com.hongjie.pms.modules.following.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.common.annotation.RedisRateLimit;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.CommonResult;
import com.hongjie.pms.modules.following.service.FollowService;
import com.hongjie.pms.modules.user.dto.UserSimpleDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

/**
 * 关注
 */
@Slf4j
@RestController
@RequestMapping("/pet-system")
@RequiredArgsConstructor
public class FollowingController {

    private final FollowService followService;

    /**
     * 获取关注列表
     */
    @GetMapping("/following/list")
    public CommonResult<IPage<UserSimpleDto>> listFollowing(@RequestParam int pageNum, @RequestParam int pageSize) {
        log.info("获取关注列表");
        IPage<UserSimpleDto> page = followService.listFollowing(UserContext.getUserId(), pageNum, pageSize);
        return CommonResult.success(page);
    }

    /**
     * 获取粉丝列表
     */
    @GetMapping("/follower/list")
    public CommonResult<IPage<UserSimpleDto>> listFollower(@RequestParam int pageNum, @RequestParam int pageSize) {
        IPage<UserSimpleDto> page = followService.listFollower(UserContext.getUserId(), pageNum, pageSize);
        return CommonResult.success(page);
    }

    /**
     * 关注用户
     */
    @RedisRateLimit(key = "followUser", capacity = 5, refillRate = 5, duration = 1, timeUnit = TimeUnit.SECONDS)
    @PostMapping("/follow")
    public CommonResult<String> followUser(@RequestParam Long userId) {
        Long currentUserId = UserContext.getUserId();
        log.info("关注");
        followService.followUser(currentUserId, userId);
        return CommonResult.success("关注成功");
    }

}
