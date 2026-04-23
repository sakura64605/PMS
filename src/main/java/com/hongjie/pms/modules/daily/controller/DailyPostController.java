package com.hongjie.pms.modules.daily.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.CommonResult;
import com.hongjie.pms.modules.daily.dto.DailyPostDto;
import com.hongjie.pms.modules.daily.dto.PublishDailyRequest;
import com.hongjie.pms.modules.daily.entity.DailyPost;
import com.hongjie.pms.modules.daily.entity.Topic;
import com.hongjie.pms.modules.daily.service.DailyPostService;
import com.hongjie.pms.modules.daily.service.DailyRecommendService;
import com.hongjie.pms.modules.daily.service.TopicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 日记帖子
 */
@Slf4j
@RestController
@RequestMapping("/pet-system/daily")
@RequiredArgsConstructor
public class DailyPostController {

    private final DailyPostService dailyPostService;
    private final TopicService topicService;
    private final DailyRecommendService dailyRecommendService;

    /**
     * 发表日记帖子
     */
    @PostMapping("/publish")
    public CommonResult<DailyPostDto> publish(@RequestBody PublishDailyRequest request) {
        return CommonResult.success(dailyPostService.publish(request));
    }

    /**
     * 获取推荐动态（新版推荐算法）
     */
    @GetMapping("/feed")
    public CommonResult<IPage<DailyPostDto>> getFeed(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Long userId = UserContext.getUserId();
        return CommonResult.success(dailyRecommendService.recommend(userId, pageNum, pageSize));
    }

    /**
     * 记录用户行为（前端埋点）
     */
    @PostMapping("/action")
    public CommonResult<Void> recordAction(@RequestParam Long targetId, @RequestParam String actionType) {
        Long userId = UserContext.getUserId();
        dailyRecommendService.recordAction(userId, targetId, actionType);
        return CommonResult.success();
    }

    /**
     * 获取详情
     */
    @GetMapping("/{id}")
    public CommonResult<DailyPostDto> getDetail(@PathVariable Long id) {
        return CommonResult.success(dailyPostService.getDetail(id));
    }

    /**
     * 点赞
     */
    @PostMapping("/{id}/like")
    public CommonResult<Boolean> like(@PathVariable Long id) {
        return CommonResult.success(dailyPostService.like(id));
    }

    /**
     * 删除
     */
    @DeleteMapping("/{id}")
    public CommonResult<String> delete(@PathVariable Long id) {
        dailyPostService.delete(id);
        return CommonResult.success("删除成功");
    }

    /**
     * 获取热门话题
     */
    @GetMapping("/topics/hot")
    public CommonResult<List<Topic>> getHotTopics(@RequestParam(defaultValue = "10") Integer limit) {
        return CommonResult.success(topicService.getHotTopics(limit));
    }

    /**
     * 搜索话题
     */
    @GetMapping("/topics/search")
    public CommonResult<List<Topic>> searchTopics(@RequestParam String keyword) {
        return CommonResult.success(topicService.searchTopics(keyword, 20));
    }

    /**
     * 创建话题
     */
    @PostMapping("/topics/create")
    public CommonResult<Topic> createTopic(@RequestParam String name, @RequestParam String description) {
        return CommonResult.success(topicService.createTopic(name, description));
    }
}