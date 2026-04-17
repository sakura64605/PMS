package com.hongjie.pms.modules.feed.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.CommonResult;
import com.hongjie.pms.modules.feed.dto.FeedDto;
import com.hongjie.pms.modules.feed.service.FeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Feed 控制器
 */
@Slf4j
@RestController
@RequestMapping("/pet-system/feed")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;

    /**
     * 获取首页 Feed 流
     */
    @GetMapping("/home")
    public CommonResult<IPage<FeedDto>> getHomeFeed(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        
        log.info("获取首页Feed: pageNum={}, pageSize={}", pageNum, pageSize);
        IPage<FeedDto> feed = feedService.getHomeFeed(pageNum, pageSize);
        return CommonResult.success(feed);
    }

    /**
     * 获取未读消息数量
     */
    @GetMapping("/unread-count")
    public CommonResult<Integer> getUnreadCount() {
        int count = feedService.getUnreadCount();
        return CommonResult.success(count);
    }
}