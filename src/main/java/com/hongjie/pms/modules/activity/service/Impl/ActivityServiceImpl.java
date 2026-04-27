package com.hongjie.pms.modules.activity.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.common.circuitbreaker.annotation.CircuitBreaker;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.cache.DistributedCache;
import com.hongjie.pms.common.cache.toolkit.CacheUtil;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.enums.PostType;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.exception.SystemException;
import com.hongjie.pms.modules.activity.dto.request.ActivityListRequestDto;
import com.hongjie.pms.modules.activity.dto.request.SignUpInfoRequest;
import com.hongjie.pms.modules.activity.dto.response.ActivityDetailRespDto;
import com.hongjie.pms.modules.activity.dto.response.ActivityListRespDto;
import com.hongjie.pms.modules.activity.dto.request.ActivityRequestDto;
import com.hongjie.pms.modules.activity.dto.response.ActivityPostRespDto;
import com.hongjie.pms.modules.activity.dto.response.SignUpResponse;
import com.hongjie.pms.modules.activity.entity.Activity;
import com.hongjie.pms.modules.activity.entity.ActivitySignup;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.activity.mapper.ActivitySignupMapper;
import com.hongjie.pms.modules.activity.service.ActivityService;
import com.hongjie.pms.modules.audit.service.AuditService;
import com.hongjie.pms.modules.feed.service.FeedService;
import com.hongjie.pms.modules.following.entity.Follow;
import com.hongjie.pms.modules.following.mapper.FollowMapper;
import com.hongjie.pms.modules.like.entity.LikeRecord;
import com.hongjie.pms.modules.like.mapper.LikeRecordMapper;
import com.hongjie.pms.modules.message.service.MessageService;
import com.hongjie.pms.common.punishment.scheduler.DelayTaskService;
import com.hongjie.pms.modules.search.event.ActivityPublishedEvent;
import com.hongjie.pms.modules.user.dto.UserSimpleDto;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.common.enums.CommentLikeTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityServiceImpl implements ActivityService {

    private final UserMapper userMapper;
    private final ActivityMapper activityMapper;
    private final ActivitySignupMapper activitySignupMapper;
    private final LikeRecordMapper likeRecordMapper;
    private final FollowMapper followMapper;
    private final MessageService messageService;
    private final DistributedCache distributedCache;
    private final DelayTaskService delayTaskService;
    private final FeedService feedService;
    private final AuditService auditService;
    private final RedissonClient redissonClient;
    private final ApplicationEventPublisher eventPublisher;


    @Override
    public ActivityPostRespDto postActivity(ActivityRequestDto request) {

        // 校验时间
        log.info("开始时间: {}", request.getStartTime(), "结束时间: {}", request.getEndTime(), "当前时间：{}", LocalDateTime.now());
        if (request.getStartTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "开始时间不能早于当前时间");
        }
        if (request.getEndTime().isBefore(request.getStartTime())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "结束时间不能早于开始时间");
        }

        Activity activity = new Activity();
        Long userId = UserContext.getUserId();
        activity.setUserId(userId);
        activity.setTitle(request.getTitle());
        activity.setContent(request.getContent());
        activity.setImages(request.getImages());
        activity.setLocation(request.getLocation());
        activity.setMaxPeople(request.getMaxPeople());
        activity.setStartTime(request.getStartTime());
        activity.setEndTime(request.getEndTime());
        activity.setStatus(0);
        activity.setAuditStatus(0);
        activityMapper.insert(activity);

        // 活动开始前30分钟提醒
        LocalDateTime remindTime = request.getStartTime().minusMinutes(30);
        if (remindTime.isAfter(LocalDateTime.now())) {
            delayTaskService.addTask("ACTIVITY_REMIND", activity.getId(), remindTime);
        }

        // 活动开始
        LocalDateTime startTime = request.getStartTime();
        delayTaskService.addTask("ACTIVITY_START", activity.getId(), startTime);

        // 活动结束后1分钟统计
        LocalDateTime statisticsTime = request.getEndTime().plusMinutes(1);
        if (statisticsTime.isAfter(LocalDateTime.now())) {
            delayTaskService.addTask("ACTIVITY_STATISTICS", activity.getId(), statisticsTime);
        }

        // 清理活动列表缓存
        String activityListCacheKey = CacheUtil.buildKey("activityList", "1", "10");
        distributedCache.delete(activityListCacheKey);

        ActivityPostRespDto response = ActivityPostRespDto.builder()
                .id(activity.getId())
                .userId(activity.getUserId())
                .title(activity.getTitle())
                .content(activity.getContent())
                .images(activity.getImages())
                .location(activity.getLocation())
                .maxPeople(activity.getMaxPeople())
                .startTime(activity.getStartTime())
                .endTime(activity.getEndTime())
                .status(activity.getStatus())
                .build();

        auditService.submit(PostType.ACTIVITY.getCode(), activity.getId());

        // 获取用户信息
        User user = userMapper.selectById(userId);

        // 推送 Feed
        feedService.pushToFans(
                userId,
                activity.getId(),
                PostType.ACTIVITY.getCode(),
                activity.getTitle(),
                activity.getImages(),
                user.getUserName(),
                user.getAvatar(),
                activity.getCreateTime()
        );
        eventPublisher.publishEvent(new ActivityPublishedEvent(this, activity));

        return response;
    }

    @Override
    @Transactional
    public void updateActivity(ActivityRequestDto activityRequestDto) {
        // 校验时间
        if (activityRequestDto.getStartTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "开始时间不能早于当前时间");
        }
        if (activityRequestDto.getEndTime().isBefore(activityRequestDto.getStartTime())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "结束时间不能早于开始时间");
        }

        Long userId = UserContext.getUserId();
        if(activityRequestDto.getUserId() != null && !activityRequestDto.getUserId().equals(userId)){
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Activity activity = activityMapper.selectById(activityRequestDto.getId());
        log.info("updateActivity: userId={}, activity={}", userId, activity);
        if (activity == null) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        if(activity.getStatus() == 2){
            throw new BusinessException(ErrorCode.ACTIVITY_ENDED, "活动已结束，不可修改");
        }

        // 检测是否有重大变更
        boolean hasMajorChange = hasMajorChange(activity, activityRequestDto);

        // TODO:如果有重大变更，标记所有报名用户需要重新确认

        activity.setId(activityRequestDto.getId());
        activity.setTitle(activityRequestDto.getTitle());
        activity.setContent(activityRequestDto.getContent());
        activity.setImages(activityRequestDto.getImages());
        activity.setLocation(activityRequestDto.getLocation());
        activity.setMaxPeople(activityRequestDto.getMaxPeople());
        activity.setStartTime(activityRequestDto.getStartTime());
        activity.setEndTime(activityRequestDto.getEndTime());
        activity.setAuditStatus(hasMajorChange ? 0 : activity.getAuditStatus());
        Integer result = activityMapper.updateById(activity);

        // 清理缓存
        String cacheKey = CacheUtil.buildKey("activity", String.valueOf(activityRequestDto.getId()));
        distributedCache.delete(cacheKey);

        if (result <= 0) {
            throw new SystemException(ErrorCode.DB_ERROR);
        }
    }

    @Override
    public void deleteActivity(Long id) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        if(activity.getStatus() == 2){
            throw new BusinessException(ErrorCode.ACTIVITY_ENDED, "活动已结束，不可删除");
        }
        if(activity.getUserId() != UserContext.getUserId()){
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        activity.setDeleted(1);
        Integer result = activityMapper.updateById(activity);

        // 清理缓存
        String cacheKey = CacheUtil.buildKey("activity", String.valueOf(id));
        distributedCache.delete(cacheKey);

        if (result <= 0) {
            throw new SystemException(ErrorCode.DB_ERROR);
        }
    }

    @CircuitBreaker(
            value = "getActivityList",
            windowSize = 10,
            minRequestAmount = 3,
            errorRateThreshold = 0.5,
            openDurationSeconds = 10,
            fallbackMethod = "fallbackGetActivityList"
    )
    @Override
    public IPage<ActivityListRespDto> getActivityList(ActivityListRequestDto request) {
        String cacheKey = CacheUtil.buildKey("activityList",
                request.getPageNum() != null ? request.getPageNum().toString() : "",
                request.getPageSize() != null ? request.getPageSize().toString() : "",
                request.getStatus() != null ? request.getStatus().toString() : "",
                request.getUserId() != null ? request.getUserId().toString() : "",
                request.getKeyword() != null ? request.getKeyword() : "",
                request.getLocation() != null ? request.getLocation() : "",
                request.getOrderBy() != null ? request.getOrderBy() : "",
                request.getOrder() != null ? request.getOrder() : ""
        );

        Page<ActivityListRespDto> cachedPage = distributedCache.get(cacheKey, Page.class);
        if (cachedPage != null) {
            log.info("从缓存获取活动列表: key={}", cacheKey);
            return cachedPage;
        }

        LambdaQueryWrapper<Activity> wrapper = new LambdaQueryWrapper<>();

        if (request.getStatus() != null) {
            wrapper.eq(Activity::getStatus, request.getStatus());
        } else {
            wrapper.in(Activity::getStatus, List.of(0, 1, 2));
        }

        if (request.getUserId() != null) {
            wrapper.eq(Activity::getUserId, request.getUserId());
        }

        if (request.getStartDate() != null) {
            wrapper.ge(Activity::getStartTime, request.getStartDate());
        }
        if (request.getEndDate() != null) {
            wrapper.le(Activity::getStartTime, request.getEndDate());
        }

        if (StringUtils.hasText(request.getLocation())) {
            wrapper.like(Activity::getLocation, request.getLocation());
        }

        if (StringUtils.hasText(request.getKeyword())) {
            wrapper.and(w -> w
                    .like(Activity::getTitle, request.getKeyword())
                    .or()
                    .like(Activity::getContent, request.getKeyword())
            );
        }

        if ("startTime".equals(request.getOrderBy())) {
            if ("asc".equals(request.getOrder())) {
                wrapper.orderByAsc(Activity::getStartTime);
            } else {
                wrapper.orderByDesc(Activity::getStartTime);
            }
        } else {
            if ("asc".equals(request.getOrder())) {
                wrapper.orderByAsc(Activity::getCreateTime);
            } else {
                wrapper.orderByDesc(Activity::getCreateTime);
            }
        }

        wrapper.eq(Activity::getDeleted, 0);
        wrapper.eq(Activity::getAuditStatus, 1);

        Page<Activity> page = new Page<>(request.getPageNum(), request.getPageSize());
        IPage<Activity> activityPage = activityMapper.selectPage(page, wrapper);

        if (activityPage.getRecords().isEmpty()) {
            Page<ActivityListRespDto> emptyPage = new Page<>(request.getPageNum(), request.getPageSize(), 0);
            emptyPage.setRecords(new ArrayList<>());
            return emptyPage;
        }

        List<Activity> records = activityPage.getRecords();

        // 批量查询用户信息
        List<Long> userIds = records.stream()
                .map(Activity::getUserId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, UserSimpleDto> userMap;
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            userMap = users.stream().collect(Collectors.toMap(
                    User::getId,
                    user -> UserSimpleDto.builder()
                            .userId(user.getId())
                            .username(user.getUserName())
                            .nickname(user.getNickName())
                            .avatar(user.getAvatar())
                            .build()
            ));
        } else {
            userMap = new HashMap<>();
        }

        // 批量查询报名状态
        Map<Long, Integer> signupMap;
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null && !records.isEmpty()) {
            List<Long> activityIds = records.stream()
                    .map(Activity::getId)
                    .collect(Collectors.toList());

            List<ActivitySignup> signups = activitySignupMapper.selectList(
                    new LambdaQueryWrapper<ActivitySignup>()
                            .eq(ActivitySignup::getUserId, currentUserId)
                            .in(ActivitySignup::getActivityId, activityIds)
            );
            signupMap = signups.stream()
                    .collect(Collectors.toMap(ActivitySignup::getActivityId, s -> 1));
        } else {
            signupMap = new HashMap<>();
        }

        // 转换为 DTO
        List<ActivityListRespDto> recordsDto = records.stream().map(activity -> {
            UserSimpleDto user = userMap.get(activity.getUserId());
            if (user == null) {
                user = UserSimpleDto.builder()
                        .userId(activity.getUserId())
                        .username("已注销")
                        .nickname("已注销")
                        .build();
            }

            String firstImage = null;
            if (activity.getImages() != null && !activity.getImages().isEmpty()) {
                firstImage = activity.getImages().get(0);
            }

            return ActivityListRespDto.builder()
                    .id(activity.getId())
                    .title(activity.getTitle())
                    .images(firstImage)
                    .location(activity.getLocation())
                    .maxPeople(activity.getMaxPeople())
                    .currentPeople(activity.getCurrentPeople())
                    .startTime(activity.getStartTime())
                    .endTime(activity.getEndTime())
                    .status(activity.getStatus())
                    .auditStatus(activity.getAuditStatus())
                    .viewCount(activity.getViewCount())
                    .likeCount(activity.getLikeCount())
                    .commentCount(activity.getCommentCount())
                    .createTime(activity.getCreateTime())
                    .user(user)
                    .isSignedUp(signupMap.getOrDefault(activity.getId(), 0))
                    .build();
        }).collect(Collectors.toList());

        Page<ActivityListRespDto> resultPage = new Page<>(
                activityPage.getCurrent(),
                activityPage.getSize(),
                activityPage.getTotal()
        );
        resultPage.setRecords(recordsDto);

        // 放入缓存，设置过期时间为2分钟
        distributedCache.put(cacheKey, resultPage, 120);

        return resultPage;
    }

    /**
     * 降级方法：返回空列表
     */
    public IPage<ActivityListRespDto> fallbackGetActivityList(ActivityListRequestDto request) {
        log.warn("活动列表熔断降级: pageNum={}, pageSize={}", request.getPageNum(), request.getPageSize());

        Page<ActivityListRespDto> emptyPage = new Page<>(request.getPageNum(), request.getPageSize(), 0);
        emptyPage.setRecords(new ArrayList<>());
        return emptyPage;
    }

    public IPage<ActivityListRespDto> fallbackGetActivityList(ActivityListRequestDto request, Exception e) {
        log.error("活动列表熔断降级: error={}", e.getMessage());
        return fallbackGetActivityList(request);
    }

    @CircuitBreaker(
            value = "getActivityDetail",
            windowSize = 10,
            minRequestAmount = 3,
            errorRateThreshold = 0.5,
            openDurationSeconds = 10,
            fallbackMethod = "fallbackGetActivityDetail"
    )
    @Override
    @Transactional
    public ActivityDetailRespDto getActivityDetail(Long id) {
        // 1. 先查缓存
        String cacheKey = CacheUtil.buildKey("activity", String.valueOf(id));
        ActivityDetailRespDto cached = distributedCache.get(cacheKey, ActivityDetailRespDto.class);
        if (cached != null) {
            log.info("从缓存获取活动详情: id={}", id);
            return cached;
        }

        // 3. 查数据库
        Activity activity = activityMapper.selectById(id);

        if (activity.getAuditStatus() != 1){
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null && !currentUserId.equals(activity.getUserId())) {
            activity.setViewCount(activity.getViewCount() + 1);
            activityMapper.updateById(activity);
        }

        LambdaQueryWrapper<ActivitySignup> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ActivitySignup::getActivityId, id)
                .eq(ActivitySignup::getUserId, currentUserId);
        ActivitySignup activitySignup = activitySignupMapper.selectOne(queryWrapper);

        ActivityDetailRespDto activityDetailRespDto = new ActivityDetailRespDto();
        activityDetailRespDto.setId(activity.getId());

        if (activitySignup != null) {
            activityDetailRespDto.setIsSignUp(1);
        }

        LikeRecord likeRecord = likeRecordMapper.selectOne(
                new LambdaQueryWrapper<LikeRecord>()
                        .eq(LikeRecord::getTargetId, id)
                        .eq(LikeRecord::getUserId, currentUserId)
                        .eq(LikeRecord::getTargetType, CommentLikeTypes.PET_ACTIVITY)
        );
        if (likeRecord != null) {
            activityDetailRespDto.setIsLike(1);
        } else {
            activityDetailRespDto.setIsLike(0);
        }

        User user = userMapper.selectById(activity.getUserId());
        UserSimpleDto userSimpleDto = UserSimpleDto.builder()
                .userId(user.getId())
                .username(user.getUserName())
                .nickname(user.getNickName())
                .avatar(user.getAvatar())
                .build();

        LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Follow::getFollowerId, currentUserId);
        wrapper.eq(Follow::getFollowingId, activity.getUserId());
        Follow follow = followMapper.selectOne(wrapper);
        if (follow != null) {
            userSimpleDto.setIsFollow(true);
        }

        activityDetailRespDto.setUser(userSimpleDto);
        activityDetailRespDto.setTitle(activity.getTitle());
        activityDetailRespDto.setContent(activity.getContent());
        activityDetailRespDto.setImages(activity.getImages());
        activityDetailRespDto.setLocation(activity.getLocation());
        activityDetailRespDto.setMaxPeople(activity.getMaxPeople());
        activityDetailRespDto.setCurrentPeople(activity.getCurrentPeople());
        activityDetailRespDto.setStartTime(activity.getStartTime());
        activityDetailRespDto.setEndTime(activity.getEndTime());
        activityDetailRespDto.setStatus(activity.getStatus());
        activityDetailRespDto.setViewCount(activity.getViewCount());
        activityDetailRespDto.setLikeCount(activity.getLikeCount());
        activityDetailRespDto.setCommentCount(activity.getCommentCount());
        activityDetailRespDto.setShareCount(activity.getShareCount());
        activityDetailRespDto.setCreateTime(activity.getCreateTime());
        activityDetailRespDto.setUpdateTime(activity.getUpdateTime());
        activityDetailRespDto.setDeleted(activity.getDeleted());

        // 4. 写入缓存（30分钟）
        distributedCache.put(cacheKey, activityDetailRespDto, 1800);

        return activityDetailRespDto;
    }

    /**
     * 降级方法
     */
    public ActivityDetailRespDto fallbackGetActivityDetail(Long id) {
        return fallbackGetActivityDetail(id, null);
    }

    public ActivityDetailRespDto fallbackGetActivityDetail(Long id, Exception e) {
        if (e != null) {
            log.error("活动详情熔断降级: id={}, error={}", id, e.getMessage());
        }

        // 1. 从降级缓存读取
        String cacheKey = CacheUtil.buildKey("activity:fallback", String.valueOf(id));
        ActivityDetailRespDto cached = distributedCache.get(cacheKey, ActivityDetailRespDto.class);
        if (cached != null) {
            log.info("从降级缓存读取活动详情: id={}", id);
            return cached;
        }

        // 2. 返回降级响应
        ActivityDetailRespDto fallbackResp = new ActivityDetailRespDto();
        fallbackResp.setId(id);
        fallbackResp.setStatus(0);
        fallbackResp.setTitle("服务繁忙，请稍后再试");
        fallbackResp.setContent("系统繁忙，数据暂时无法获取");
        fallbackResp.setViewCount(0);
        fallbackResp.setLikeCount(0);
        fallbackResp.setCommentCount(0);
        fallbackResp.setShareCount(0);
        fallbackResp.setIsSignUp(0);
        fallbackResp.setIsLike(0);

        UserSimpleDto emptyUser = UserSimpleDto.builder()
                .userId(0L)
                .username("system")
                .nickname("系统")
                .build();
        fallbackResp.setUser(emptyUser);

        // 缓存降级响应（60秒）
        distributedCache.put(cacheKey, fallbackResp, 60);

        return fallbackResp;
    }

    @Override
    @Transactional
    public void signUp(SignUpInfoRequest request) {
        Long currentUserId = UserContext.getUserId();
        Long activityId = request.getActivityId();

        // 1. 先查询活动（不加锁）
        Activity activity = activityMapper.selectById(activityId);

        // 审核状态校验
        if (activity.getAuditStatus() == 2) {
            throw new BusinessException(ErrorCode.AUDIT_REJECT);
        } else if (activity.getAuditStatus() == 0) {
            throw new BusinessException(ErrorCode.AUDIT_WAITING);
        }

        // 业务校验（不加锁）
        if (activity.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能报名自己的活动");
        }
        if (activity.getStatus() != 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "活动不在报名中");
        }

        // 检查是否已报名（不加锁）
        boolean alreadySignUp = activitySignupMapper.exists(new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getActivityId, activityId)
                .eq(ActivitySignup::getUserId, currentUserId));
        if (alreadySignUp) {
            throw new BusinessException(ErrorCode.ACTIVITY_SIGNUP_EXISTS);
        }

        // ==================== 只锁核心操作 ====================
        String lockKey = "lock:activity:signup:" + activityId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 锁等待时间缩短到 1 秒
            boolean locked = lock.tryLock(1, 2, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "系统繁忙，请稍后再试");
            }

            // 双重检查（防止锁等待期间被占满）
            Activity latestActivity = activityMapper.selectById(activityId);
            if (latestActivity.getCurrentPeople() >= latestActivity.getMaxPeople()) {
                throw new BusinessException(ErrorCode.ACTIVITY_FULL);
            }

            if (activity.getCurrentPeople() >= activity.getMaxPeople()) {
                throw new BusinessException(ErrorCode.ACTIVITY_FULL);
            }

            // 保存报名记录
            ActivitySignup activitySignup = new ActivitySignup();
            activitySignup.setActivityId(activityId);
            activitySignup.setUserId(currentUserId);
            activitySignup.setStatus(1);
            activitySignup.setRealName(request.getRealName());
            activitySignup.setPhone(request.getPhone());
            activitySignup.setRemark(request.getRemark());
            activitySignupMapper.insert(activitySignup);

            // 更新报名人数（使用乐观锁）
            int updateResult = activityMapper.incrementCurrentPeople(activityId);
            if (updateResult == 0) {
                throw new BusinessException(ErrorCode.ACTIVITY_FULL, "报名人数已满");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "系统繁忙，请稍后再试");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        // ==================== 锁外操作（不影响性能） ====================

        // 更新缓存（异步）
        CompletableFuture.runAsync(() -> {
            // 清除活动缓存
            String activityCacheKey = CacheUtil.buildKey("activity", String.valueOf(activityId));
            distributedCache.delete(activityCacheKey);
            
            // 清除活动列表缓存
            String activityListCacheKey = CacheUtil.buildKey("activityList", "1", "10");
            distributedCache.delete(activityListCacheKey);
            
            // 清除用户活动缓存
            String userActivityCacheKey = CacheUtil.buildKey("userActivity", String.valueOf(currentUserId));
            distributedCache.delete(userActivityCacheKey);
        });

        // 发送通知（异步）
        Activity finalActivity = activity;
        CompletableFuture.runAsync(() -> {
            if (finalActivity.getCurrentPeople() + 1 == finalActivity.getMaxPeople()) {
                messageService.sendActivityFullNotification(
                        finalActivity.getUserId(), finalActivity.getTitle(), activityId);
            }
            messageService.sendSomeoneSignUpNotification(
                    finalActivity.getUserId(), currentUserId, request.getRealName(),
                    finalActivity.getTitle(), activityId);
            messageService.sendSignUpSuccessNotification(
                    currentUserId, finalActivity.getTitle(), activityId);
        });

        log.info("用户{}报名活动{}成功", currentUserId, activityId);
    }

    /**
     * 降级方法：报名失败时的处理
     */
    public void fallbackSignUp(SignUpInfoRequest request) {
        log.warn("报名熔断降级: activityId={}", request.getActivityId());
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "报名服务繁忙，请稍后再试");
    }

    public void fallbackSignUp(SignUpInfoRequest request, Exception e) {
        log.error("报名熔断降级: activityId={}, error={}", request.getActivityId(), e.getMessage());
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "报名服务繁忙，请稍后再试");
    }

    @Override
    @Transactional
    public void cancelSignUp(Long id) {
        Long currentUserId = UserContext.getUserId();
        Activity activity = activityMapper.selectById(id);
        ActivitySignup activitySignup = activitySignupMapper.selectOne(new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getActivityId, id)
                .eq(ActivitySignup::getUserId, currentUserId));
        if (activitySignup == null) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_SIGNUP);
        }
        activitySignupMapper.deleteById(activitySignup);
        activityMapper.decrementCurrentPeople(id);
        activityMapper.updateById(activity);
    }

    @Override
    public IPage<ActivityListRespDto> getRecycleBinList(ActivityListRequestDto request) {
        LambdaQueryWrapper<Activity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Activity::getDeleted, 1);

        Long userId = UserContext.getUserId();
        wrapper.eq(Activity::getUserId, userId);

        if (request.getUserId() != null) {
            wrapper.eq(Activity::getUserId, request.getUserId());
        }

        // 时间筛选
        if (request.getStartDate() != null) {
            wrapper.ge(Activity::getStartTime, request.getStartDate());
        }
        if (request.getEndDate() != null) {
            wrapper.le(Activity::getStartTime, request.getEndDate());
        }

        // 地点筛选
        if (StringUtils.hasText(request.getLocation())) {
            wrapper.like(Activity::getLocation, request.getLocation());
        }

        // 关键词搜索
        if (StringUtils.hasText(request.getKeyword())) {
            wrapper.and(w -> w
                    .like(Activity::getTitle, request.getKeyword())
                    .or()
                    .like(Activity::getContent, request.getKeyword())
            );
        }

        // 排序
        if ("startTime".equals(request.getOrderBy())) {
            if ("asc".equals(request.getOrder())) {
                wrapper.orderByAsc(Activity::getStartTime);
            } else {
                wrapper.orderByDesc(Activity::getStartTime);
            }
        } else {
            if ("asc".equals(request.getOrder())) {
                wrapper.orderByAsc(Activity::getCreateTime);
            } else {
                wrapper.orderByDesc(Activity::getCreateTime);
            }
        }

        // 2. 分页查询
        Page<Activity> page = new Page<>(request.getPageNum(), request.getPageSize());
        IPage<Activity> activityPage = activityMapper.selectPage(page, wrapper);

        // 3. 如果没有数据，直接返回空分页
        if (activityPage.getRecords().isEmpty()) {
            Page<ActivityListRespDto> emptyPage = new Page<>(request.getPageNum(), request.getPageSize(), 0);
            emptyPage.setRecords(new ArrayList<>());
            return emptyPage;
        }

        List<Activity> records = activityPage.getRecords();

        // 4. 批量查询用户信息
        List<Long> userIds = records.stream()
                .map(Activity::getUserId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, UserSimpleDto> userMap;
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            userMap = users.stream().collect(Collectors.toMap(
                    User::getId,
                    user -> UserSimpleDto.builder()
                            .userId(user.getId())
                            .username(user.getUserName())
                            .nickname(user.getNickName())
                            .avatar(user.getAvatar())
                            .build()
            ));
        } else {
            userMap = new HashMap<>();
        }

        // 5. 批量查询报名状态（可选，如果列表需要显示是否已报名）
        Map<Long, Integer> signupMap;
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null && !records.isEmpty()) {
            List<Long> activityIds = records.stream()
                    .map(Activity::getId)
                    .collect(Collectors.toList());

            List<ActivitySignup> signups = activitySignupMapper.selectList(
                    new LambdaQueryWrapper<ActivitySignup>()
                            .eq(ActivitySignup::getUserId, currentUserId)
                            .in(ActivitySignup::getActivityId, activityIds)
            );
            signupMap = signups.stream()
                    .collect(Collectors.toMap(ActivitySignup::getActivityId, s -> 1));
        } else {
            signupMap = new HashMap<>();
        }



        // 6. 转换为列表 DTO（注意：用 ActivityListDto，不是 DetailDto）
        List<ActivityListRespDto> recordsDto = records.stream().map(activity -> {
            UserSimpleDto user = userMap.get(activity.getUserId());

            if (user == null) {
                user = UserSimpleDto.builder()
                        .userId(activity.getUserId())
                        .username("已注销")
                        .nickname("已注销")
                        .build();
            }

            // 获取第一张图片作为封面
            String firstImage = null;
            if (activity.getImages() != null && !activity.getImages().isEmpty()) {
                firstImage = activity.getImages().get(0);
            }

            return ActivityListRespDto.builder()
                    .id(activity.getId())
                    .title(activity.getTitle())
                    .images(firstImage)      // 列表只取第一张
                    .location(activity.getLocation())
                    .maxPeople(activity.getMaxPeople())
                    .currentPeople(activity.getCurrentPeople())
                    .startTime(activity.getStartTime())
                    .endTime(activity.getEndTime())
                    .status(activity.getStatus())
                    .viewCount(activity.getViewCount())
                    .likeCount(activity.getLikeCount())
                    .commentCount(activity.getCommentCount())
                    .createTime(activity.getCreateTime())
                    .user(user)
                    .isSignedUp(signupMap.getOrDefault(activity.getId(), 0))
                    .build();
        }).collect(Collectors.toList());

        // 7. 返回分页结果
        Page<ActivityListRespDto> resultPage = new Page<>(
                activityPage.getCurrent(),
                activityPage.getSize(),
                activityPage.getTotal()
        );
        resultPage.setRecords(recordsDto);
        return resultPage;
    }

    @Override
    public void recoverActivity(Long id) {
        Long currentUserId = UserContext.getUserId();
        Activity activity = activityMapper.selectById(id);
        if (activity == null || activity.getDeleted() == 0) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        if (!UserContext.isAdmin() && !activity.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        activity.setDeleted(0);
        activityMapper.updateById(activity);

        // 清理缓存
        String cacheKey = CacheUtil.buildKey("activity", String.valueOf(id));
        distributedCache.delete(cacheKey);

    }

    @Override
    public IPage<SignUpResponse> getSignUpList(Long id, int pageNum, int pageSize) {
        Long currentUserId = UserContext.getUserId();
        Activity activity = activityMapper.selectById(id);
        if (activity == null || !activity.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        IPage<ActivitySignup> page = new Page<>(pageNum, pageSize);
        page = activitySignupMapper.selectPage(page, new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getActivityId, id)
                .orderByDesc(ActivitySignup::getCreateTime)
        );
        List<Long> userIds = page.getRecords().stream()
                .map(ActivitySignup::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, UserSimpleDto> userMap;
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            userMap = users.stream().collect(Collectors.toMap(
                    User::getId,
                    user -> UserSimpleDto.builder()
                            .userId(user.getId())
                            .username(user.getUserName())
                            .nickname(user.getNickName())
                            .avatar(user.getAvatar())
                            .build()
            ));
        } else {
            userMap = new HashMap<>();
        }
        List<SignUpResponse> recordsDto = page.getRecords().stream().map(signup -> {
            UserSimpleDto user = userMap.get(signup.getUserId());
            return SignUpResponse.builder()
                    .signupId(signup.getId())
                    .realName(signup.getRealName())
                    .phone(signup.getPhone())
                    .remark(signup.getRemark())
                    .status(signup.getStatus())
                    .signupTime(signup.getCreateTime())
                    .user(user)
                    .isCheckedIn(signup.getCheckInTime() != null)
                    .build();
        }).toList();
        Page<SignUpResponse> resultPage = new Page<>(
                page.getCurrent(),
                page.getSize(),
                page.getTotal()
        );
        resultPage.setRecords(recordsDto);
        return resultPage;
    }

    @Override
    public void signIn(Long activityId, Long userId) {
        Activity activity = activityMapper.selectById(activityId);
        Long currentUserId = UserContext.getUserId();

        if (activity == null) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }

        // 只有活动发布者可以签到
        if (!activity.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 活动已结束不能签到
        if (activity.getStatus() == 2) {
            throw new BusinessException(ErrorCode.ACTIVITY_ENDED, "活动已结束，无法签到");
        }

        // 活动结束时间已过也不能签到
        if (activity.getEndTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "活动已结束，无法签到");
        }

        ActivitySignup signup = activitySignupMapper.selectOne(new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getActivityId, activityId)
                .eq(ActivitySignup::getUserId, userId)
        );

        if (signup == null) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_SIGNUP);
        }

        if (signup.getStatus() == 4) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户已爽约，无法签到");
        }

        if (signup.getCheckInTime() != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户已签到");
        }

        signup.setCheckInTime(LocalDateTime.now());
        signup.setStatus(3); // 3-已签到
        activitySignupMapper.updateById(signup);

        messageService.sendSignInSuccessNotification(
                signup.getUserId(),
                activity.getTitle(),
                activityId
        );
    }

    /**
     * 检测是否有重大变更
     */
    private boolean hasMajorChange(Activity old, ActivityRequestDto newDto) {
        // 时间变更超过1小时
        if (old.getStartTime() != null && newDto.getStartTime() != null) {
            long diffMinutes = Duration.between(old.getStartTime(), newDto.getStartTime()).toMinutes();
            if (Math.abs(diffMinutes) > 60) {
                return true;
            }
        }

        // 地点变更
        if (old.getLocation() != null && !old.getLocation().equals(newDto.getLocation())) {
            return true;
        }

        // 人数减少
        if (newDto.getMaxPeople() != null && newDto.getMaxPeople() < old.getMaxPeople()) {
            return true;
        }

        return false;
    }
}