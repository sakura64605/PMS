package com.hongjie.pms.modules.activity.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.common.annotation.RedisRateLimit;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.CommonResult;
import com.hongjie.pms.modules.activity.dto.request.ActivityListRequestDto;
import com.hongjie.pms.modules.activity.dto.request.SignUpInfoRequest;
import com.hongjie.pms.modules.activity.dto.response.ActivityDetailRespDto;
import com.hongjie.pms.modules.activity.dto.response.ActivityListRespDto;
import com.hongjie.pms.modules.activity.dto.request.ActivityRequestDto;
import com.hongjie.pms.modules.activity.dto.response.ActivityPostRespDto;
import com.hongjie.pms.modules.activity.dto.response.SignUpResponse;
import com.hongjie.pms.modules.activity.service.ActivityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 活动
 */
@Slf4j
@RestController
@RequestMapping("/pet-system/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    /**
     * 创建活动
     */
    @RedisRateLimit(key = "postActivity", capacity = 5, refillRate = 5, duration = 1, timeUnit = TimeUnit.HOURS, message = "1小时只能发布5条活动")
    @PostMapping("/post")
    public CommonResult<ActivityPostRespDto> postActivity(@RequestBody @Valid ActivityRequestDto request) {
        ActivityPostRespDto activityListDto = activityService.postActivity(request);
        return CommonResult.success(activityListDto);
    }

    /**
     * 修改活动
     */
    @PostMapping("/update/{id}")
    public CommonResult<String> updateActivity(@RequestBody @Valid ActivityRequestDto activityRequestDto) {
        activityService.updateActivity(activityRequestDto);
        return CommonResult.success("活动更新成功");
    }

    /**
     * 删除活动
     */
    @PostMapping("/delete/{id}")
    public CommonResult<String> deleteActivity(@PathVariable Long id) {
        activityService.deleteActivity(id);
        return CommonResult.success("活动删除成功");
    }

    /**
     * 获取活动详情
     */
    @RedisRateLimit(key = "getActivityDetail", capacity = 30, refillRate = 30, duration = 1, timeUnit = TimeUnit.SECONDS)
    @GetMapping("/detail/{id}")
    public CommonResult<ActivityDetailRespDto> getActivityDetail(@PathVariable Long id) {
        ActivityDetailRespDto activityDetail = activityService.getActivityDetail(id);
        if (activityDetail == null) {
            return CommonResult.error(503, "服务繁忙，请稍后再试");
        }
        // 检查是否是降级后的默认对象
        if ("活动信息暂时不可用".equals(activityDetail.getTitle()) && "系统繁忙，请稍后再试".equals(activityDetail.getContent())) {
            return CommonResult.error(503, "服务繁忙，请稍后再试");
        }
        return CommonResult.success(activityDetail);
    }

    /**
     * 获取回收站列表
     */
    @RedisRateLimit(key = "getRecycleBinList", capacity = 30, refillRate = 30, duration = 1, timeUnit = TimeUnit.SECONDS)
    @GetMapping("/recycle-bin")
    public CommonResult<IPage<ActivityListRespDto>> getRecycleBinList(ActivityListRequestDto request) {
        log.info("获取回收站列表");
        IPage<ActivityListRespDto> activityList = activityService.getRecycleBinList(request);
        return CommonResult.success(activityList);
    }

    /**
     * 恢复活动
     */
    @PostMapping("/recover/{id}")
    public CommonResult<String> recoverActivity(@PathVariable Long id) {
        activityService.recoverActivity(id);
        return CommonResult.success("活动恢复成功");
    }

    /**
     * 查看报名人
     */
    @PostMapping("/signUpList/{id}")
    public CommonResult<IPage<SignUpResponse>> getSignUpList(@PathVariable Long id,
                                                             @RequestParam int pageNum,
                                                             @RequestParam int pageSize) {
        log.info("查看报名人");
        IPage<SignUpResponse> signUpList = activityService.getSignUpList(id, pageNum, pageSize);
        return CommonResult.success(signUpList);
    }

    /**
     * 获取活动列表
     */
    @RedisRateLimit(key = "getActivityList", capacity = 30, refillRate = 30, duration = 1, timeUnit = TimeUnit.SECONDS)
    @GetMapping("/list")
    public CommonResult<IPage<ActivityListRespDto>> getActivityList(ActivityListRequestDto request) {
        log.info("获取活动列表");
        IPage<ActivityListRespDto> activityList = activityService.getActivityList(request);
        return CommonResult.success(activityList);
    }

    /**
     * 报名活动
     */
    @PostMapping("/signUp")
    @RedisRateLimit(key = "signUp", capacity = 5, refillRate = 5, duration = 1, timeUnit = TimeUnit.SECONDS)
    public CommonResult<String> signUp(@RequestBody @Valid SignUpInfoRequest request) {
        log.info("用户报名活动");
        activityService.signUp(request);
        return CommonResult.success("报名成功");
    }

    /**
     * 活动签到
     */
    @PostMapping("/signIn")
    public CommonResult<String> signIn(@RequestParam Long activityId, @RequestParam Long userId) {
        log.info("用户签到");
        activityService.signIn(activityId, userId);
        return CommonResult.success("签到成功");
    }

    /**
     * 取消报名
     */
    @RedisRateLimit(key = "cancelSignUp", capacity = 5, refillRate = 5, duration = 1, timeUnit = TimeUnit.SECONDS)
    @PostMapping("/cancelSignUp/{id}")
    public CommonResult<String> cancelSignUp(@PathVariable Long id) {
        log.info("用户取消报名");
        activityService.cancelSignUp(id);
        return CommonResult.success("取消报名成功");
    }

    /**
     * 获取我的活动列表
     */
    @RedisRateLimit(key = "getMyActivityList", capacity = 30, refillRate = 30, duration = 1, timeUnit = TimeUnit.SECONDS)
    @GetMapping("/myActivity")
    public CommonResult<IPage<ActivityListRespDto>> getMyActivityList(ActivityListRequestDto request) {
        log.info("获取我的活动列表");
        Long userId = UserContext.getUserId();
        request.setUserId(userId);
        IPage<ActivityListRespDto> activityList = activityService.getActivityList(request);
        return CommonResult.success(activityList);
    }
}
