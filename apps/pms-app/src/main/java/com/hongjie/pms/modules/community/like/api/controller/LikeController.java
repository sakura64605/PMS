package com.hongjie.pms.modules.community.like.api.controller;

import com.hongjie.pms.platform.idempotent.annotation.Idempotent;
import com.hongjie.pms.platform.annotation.RedisRateLimit;
import com.hongjie.pms.platform.pojo.CommonResult;
import com.hongjie.pms.modules.community.like.api.dto.request.LikeRequest;
import com.hongjie.pms.modules.community.like.api.service.LikeService;
import com.hongjie.pms.modules.community.like.api.dto.response.LikeResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

/**
 * 点赞
 */
@Slf4j
@RestController
@RequestMapping("/pet-system/like")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    /**
     * 点赞
     */
    @RedisRateLimit(
            key = "likePet",
            capacity = 5,
            refillRate = 5,
            duration = 1,
            timeUnit = TimeUnit.SECONDS)
    @PostMapping()
    public CommonResult<LikeResponseDto> like(@RequestBody LikeRequest request) {
        log.info("点赞宠物信息: id={}", request.getTargetId());
        LikeResponseDto response = likeService.like(request);
        return CommonResult.success(response);
    }
}
