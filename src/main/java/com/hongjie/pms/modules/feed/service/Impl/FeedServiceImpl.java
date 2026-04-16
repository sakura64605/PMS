package com.hongjie.pms.modules.feed.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.PostType;
import com.hongjie.pms.modules.activity.entity.Activity;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.feed.dto.FeedDto;
import com.hongjie.pms.modules.feed.entity.BigVConfig;
import com.hongjie.pms.modules.feed.entity.UserInbox;
import com.hongjie.pms.modules.feed.mapper.BigVConfigMapper;
import com.hongjie.pms.modules.feed.mapper.UserInboxMapper;
import com.hongjie.pms.modules.feed.service.FeedService;
import com.hongjie.pms.modules.following.entity.Follow;
import com.hongjie.pms.modules.following.mapper.FollowMapper;
import com.hongjie.pms.modules.petpost.entity.PetPost;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedServiceImpl implements FeedService {

    private final UserInboxMapper userInboxMapper;
    private final BigVConfigMapper bigVConfigMapper;
    private final FollowMapper followMapper;
    private final PetPostMapper petPostMapper;
    private final ActivityMapper activityMapper;
    private final UserMapper userMapper;

    private static final int PUSH_THRESHOLD = 1000;  // 粉丝超过1000用拉模式
    private static final int PAGE_SIZE = 20;

    // ==================== Feed 流核心 ====================

    @Override
    public IPage<FeedDto> getHomeFeed(Integer pageNum, Integer pageSize) {
        Long userId = UserContext.getUserId();
        if (pageSize == null || pageSize <= 0) pageSize = PAGE_SIZE;
        if (pageNum == null || pageNum <= 0) pageNum = 1;

        // 1. 查询用户关注的大V（拉模式）
        LambdaQueryWrapper<BigVConfig> bigVWrapper = new LambdaQueryWrapper<BigVConfig>()
                .eq(BigVConfig::getUsePullMode, 1);
        List<BigVConfig> bigVList = bigVConfigMapper.selectList(bigVWrapper);
        Set<Long> bigVIds = bigVList.stream().map(BigVConfig::getUserId).collect(Collectors.toSet());

        // 2. 查询用户关注的人
        LambdaQueryWrapper<Follow> followWrapper = new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, userId);
        List<Follow> followList = followMapper.selectList(followWrapper);
        List<Long> followingIds = followList.stream().map(Follow::getFollowingId).collect(Collectors.toList());

        // 3. 区分大V和普通用户
        List<Long> normalUserIds = followingIds.stream()
                .filter(id -> !bigVIds.contains(id))
                .collect(Collectors.toList());
        List<Long> bigVUserIds = followingIds.stream()
                .filter(bigVIds::contains)
                .collect(Collectors.toList());

        List<FeedDto> feedList = new ArrayList<>();

        // 4. 从收件箱获取普通用户的帖子（推模式）
        if (!normalUserIds.isEmpty()) {
            Page<UserInbox> inboxPage = userInboxMapper.selectPage(
                    new Page<>(pageNum, pageSize),
                    new LambdaQueryWrapper<UserInbox>()
                            .eq(UserInbox::getUserId, userId)
                            .in(UserInbox::getPosterId, normalUserIds)
                            .orderByDesc(UserInbox::getCreateTime)
            );

            // 标记为已读
            for (UserInbox inbox : inboxPage.getRecords()) {
                if (inbox.getIsRead() == 0) {
                    inbox.setIsRead(1);
                    inbox.setReadTime(LocalDateTime.now());
                    userInboxMapper.updateById(inbox);
                }
            }

            feedList.addAll(convertInboxToFeed(inboxPage.getRecords()));
        }

        // 5. 如果第一页不够，从大V获取帖子（拉模式）
        if (feedList.size() < pageSize && !bigVUserIds.isEmpty()) {
            int needCount = pageSize - feedList.size();
            List<FeedDto> bigVPosts = getPostsFromUsers(bigVUserIds, needCount);
            feedList.addAll(bigVPosts);
        }

        // 6. 按时间排序
        feedList.sort((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()));

        // 7. 分页返回
        Page<FeedDto> resultPage = new Page<>(pageNum, pageSize, 0);
        resultPage.setRecords(feedList.stream().limit(pageSize).collect(Collectors.toList()));
        return resultPage;
    }

    @Override
    public int getUnreadCount() {
        Long userId = UserContext.getUserId();
        LambdaQueryWrapper<UserInbox> wrapper = new LambdaQueryWrapper<UserInbox>()
                .eq(UserInbox::getUserId, userId)
                .eq(UserInbox::getIsRead, 0);
        return userInboxMapper.selectCount(wrapper).intValue();
    }

    // ==================== 推模式 ====================

    @Override
    @Transactional
    public void pushToFans(Long userId, Long postId, String postType,
                           String title, List<String> images,
                           String posterName, String posterAvatar,
                           LocalDateTime createTime) {

        // 1. 查询粉丝数量，判断是否大V
        LambdaQueryWrapper<Follow> countWrapper = new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowingId, userId);
        int fansCount = followMapper.selectCount(countWrapper).intValue();

        // 2. 大V用拉模式，不推送
        if (fansCount > PUSH_THRESHOLD) {
            saveOrUpdateBigV(userId, fansCount, 1);
            log.info("大V用户使用拉模式: userId={}, fansCount={}", userId, fansCount);
            return;
        }

        // 3. 普通用户用推模式
        saveOrUpdateBigV(userId, fansCount, 0);

        // 4. 查询所有粉丝
        LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowingId, userId);
        List<Follow> followers = followMapper.selectList(wrapper);

        if (followers.isEmpty()) {
            return;
        }

        String coverImage = (images != null && !images.isEmpty()) ? images.get(0) : null;

        // 5. 批量插入收件箱
        for (Follow follow : followers) {
            UserInbox inbox = new UserInbox();
            inbox.setUserId(follow.getFollowerId());
            inbox.setPostId(postId);
            inbox.setPostType(postType);
            inbox.setPosterId(userId);
            inbox.setPosterName(posterName);
            inbox.setPosterAvatar(posterAvatar);
            inbox.setTitle(title);
            inbox.setCoverImage(coverImage);
            inbox.setCreateTime(createTime);
            inbox.setIsRead(0);
            userInboxMapper.insert(inbox);
        }

        log.info("推送Feed完成: userId={}, postId={}, 推送数量={}", userId, postId, followers.size());
    }

    // ==================== 拉模式 ====================

    /**
     * 从指定用户获取帖子（拉模式）
     */
    private List<FeedDto> getPostsFromUsers(List<Long> userIds, int limit) {
        if (userIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<FeedDto> result = new ArrayList<>();

        // 获取宠物帖子
        LambdaQueryWrapper<PetPost> petWrapper = new LambdaQueryWrapper<PetPost>()
                .in(PetPost::getUserId, userIds)
                .eq(PetPost::getStatus, 1)
                .orderByDesc(PetPost::getCreateTime)
                .last("LIMIT " + limit);
        List<PetPost> pets = petPostMapper.selectList(petWrapper);

        for (PetPost pet : pets) {
            User user = userMapper.selectById(pet.getUserId());
            result.add(FeedDto.builder()
                    .id(pet.getId())
                    .postId(pet.getId())
                    .postType(PostType.PET.getCode())
                    .title(pet.getTitle())
                    .content(pet.getContent())
                    .coverImage(pet.getImages() != null && !pet.getImages().isEmpty() ? pet.getImages().get(0) : null)
                    .viewCount(pet.getViewCount())
                    .likeCount(pet.getLikeCount())
                    .commentCount(pet.getCommentCount())
                    .posterId(pet.getUserId())
                    .posterName(user != null ? user.getUserName() : null)
                    .posterAvatar(user != null ? user.getAvatar() : null)
                    .createTime(pet.getCreateTime())
                    .build());
        }

        // 获取活动帖子
        if (result.size() < limit) {
            int remaining = limit - result.size();
            LambdaQueryWrapper<Activity> activityWrapper = new LambdaQueryWrapper<Activity>()
                    .in(Activity::getUserId, userIds)
                    .eq(Activity::getStatus, 1)
                    .orderByDesc(Activity::getCreateTime)
                    .last("LIMIT " + remaining);
            List<Activity> activities = activityMapper.selectList(activityWrapper);

            for (Activity activity : activities) {
                User user = userMapper.selectById(activity.getUserId());
                result.add(FeedDto.builder()
                        .id(activity.getId())
                        .postId(activity.getId())
                        .postType(PostType.ACTIVITY.getCode())
                        .title(activity.getTitle())
                        .content(activity.getContent())
                        .coverImage(activity.getImages() != null && !activity.getImages().isEmpty() ? activity.getImages().get(0) : null)
                        .viewCount(activity.getViewCount())
                        .likeCount(activity.getLikeCount())
                        .commentCount(activity.getCommentCount())
                        .posterId(activity.getUserId())
                        .posterName(user != null ? user.getUserName() : null)
                        .posterAvatar(user != null ? user.getAvatar() : null)
                        .createTime(activity.getCreateTime())
                        .build());
            }
        }

        return result;
    }

    // ==================== 辅助方法 ====================

    private List<FeedDto> convertInboxToFeed(List<UserInbox> inboxList) {
        List<FeedDto> result = new ArrayList<>();

        // 按类型分组
        Map<String, List<UserInbox>> groupMap = inboxList.stream()
                .collect(Collectors.groupingBy(UserInbox::getPostType));

        // 处理宠物帖子
        if (groupMap.containsKey(PostType.PET.getCode())) {
            List<Long> petIds = groupMap.get(PostType.PET.getCode()).stream()
                    .map(UserInbox::getPostId)
                    .collect(Collectors.toList());
            List<PetPost> pets = petPostMapper.selectBatchIds(petIds);
            Map<Long, PetPost> petMap = pets.stream()
                    .collect(Collectors.toMap(PetPost::getId, p -> p));

            for (UserInbox inbox : groupMap.get(PostType.PET.getCode())) {
                PetPost pet = petMap.get(inbox.getPostId());
                if (pet != null && pet.getStatus() == 1) {
                    result.add(FeedDto.builder()
                            .id(pet.getId())
                            .postId(pet.getId())
                            .postType(PostType.PET.getCode())
                            .title(pet.getTitle())
                            .content(pet.getContent())
                            .coverImage(inbox.getCoverImage())
                            .viewCount(pet.getViewCount())
                            .likeCount(pet.getLikeCount())
                            .commentCount(pet.getCommentCount())
                            .posterId(pet.getUserId())
                            .posterName(inbox.getPosterName())
                            .posterAvatar(inbox.getPosterAvatar())
                            .createTime(inbox.getCreateTime())
                            .build());
                }
            }
        }

        // 处理活动帖子
        if (groupMap.containsKey(PostType.ACTIVITY.getCode())) {
            List<Long> activityIds = groupMap.get(PostType.ACTIVITY.getCode()).stream()
                    .map(UserInbox::getPostId)
                    .collect(Collectors.toList());
            List<Activity> activities = activityMapper.selectBatchIds(activityIds);
            Map<Long, Activity> activityMap = activities.stream()
                    .collect(Collectors.toMap(Activity::getId, a -> a));

            for (UserInbox inbox : groupMap.get(PostType.ACTIVITY.getCode())) {
                Activity activity = activityMap.get(inbox.getPostId());
                if (activity != null && activity.getStatus() == 1) {
                    result.add(FeedDto.builder()
                            .id(activity.getId())
                            .postId(activity.getId())
                            .postType(PostType.ACTIVITY.getCode())
                            .title(activity.getTitle())
                            .content(activity.getContent())
                            .coverImage(inbox.getCoverImage())
                            .viewCount(activity.getViewCount())
                            .likeCount(activity.getLikeCount())
                            .commentCount(activity.getCommentCount())
                            .posterId(activity.getUserId())
                            .posterName(inbox.getPosterName())
                            .posterAvatar(inbox.getPosterAvatar())
                            .createTime(inbox.getCreateTime())
                            .build());
                }
            }
        }

        return result;
    }

    private void saveOrUpdateBigV(Long userId, int fansCount, int usePullMode) {
        BigVConfig config = bigVConfigMapper.selectById(userId);
        if (config == null) {
            config = new BigVConfig();
            config.setUserId(userId);
            config.setFansCount(fansCount);
            config.setUsePullMode(usePullMode);
            config.setUpdateTime(LocalDateTime.now());
            bigVConfigMapper.insert(config);
        } else {
            config.setFansCount(fansCount);
            config.setUsePullMode(usePullMode);
            config.setUpdateTime(LocalDateTime.now());
            bigVConfigMapper.updateById(config);
        }
    }

    @Override
    @Transactional
    public void onUnfollow(Long followerId, Long followingId) {
        LambdaQueryWrapper<UserInbox> wrapper = new LambdaQueryWrapper<UserInbox>()
                .eq(UserInbox::getUserId, followerId)
                .eq(UserInbox::getPosterId, followingId);
        userInboxMapper.delete(wrapper);
        log.info("取消关注，清理收件箱: followerId={}, followingId={}", followerId, followingId);
    }
}