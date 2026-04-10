package com.hongjie.pms.modules.like.controller;

import com.hongjie.pms.common.annotation.RateLimit;
import com.hongjie.pms.common.pojo.CommonResult;
import com.hongjie.pms.modules.like.dto.request.LikeRequest;
import com.hongjie.pms.modules.like.service.LikeService;
import com.hongjie.pms.modules.like.dto.response.LikeResponseDto;
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
    @RateLimit(key = "likePet", count = 5, timeUnit = TimeUnit.SECONDS)
    @PostMapping()
    public CommonResult<LikeResponseDto> like(@RequestBody LikeRequest request) {
        log.info("点赞宠物信息: id={}", request.getTargetId());
        LikeResponseDto response = likeService.like(request);
        return CommonResult.success(response);
    }

}
