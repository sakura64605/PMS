package com.hongjie.pms.modules.following.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.following.entity.Follow;
import com.hongjie.pms.modules.following.mapper.FollowMapper;
import com.hongjie.pms.modules.following.service.FollowService;
import com.hongjie.pms.modules.message.service.MessageService;
import com.hongjie.pms.modules.user.dto.UserSimpleDto;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private final FollowMapper followMapper;
    private final UserMapper userMapper;
    private final MessageService messageService;

    @Override
    public void followUser(Long currentUserId, Long userId) {

        if (currentUserId.equals(userId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能关注自己");
        }
        Follow follow = new Follow();
        follow.setFollowerId(currentUserId);
        follow.setFollowingId(userId);

        LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Follow::getFollowerId, userId)
                .eq(Follow::getFollowingId, currentUserId);
        Follow existFollow = followMapper.selectOne(queryWrapper);

        if (existFollow != null) {
            log.info("取消关注");
            followMapper.deleteById(existFollow);
            userMapper.decreaseFollowerCount(userId);
            userMapper.decreaseFollowingCount(currentUserId);
        } else {
            log.info("用户{}关注用户{}", currentUserId, userId);
            followMapper.insert(follow);
            userMapper.increaseFollowerCount(userId);
            userMapper.increaseFollowingCount(currentUserId);
        }

        if (existFollow == null) {
            // 新增关注
            messageService.sendFollowNotification(
                    userId,
                    currentUserId,
                    UserContext.getUserName(),
                    "/user/" + userId
            );
        }

    }

    //TODO 查看他人的关注列表和粉丝列表
    @Override
    public IPage<UserSimpleDto> listFollowing(Long userId, int pageNum, int pageSize) {
        Page<Follow> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Follow::getFollowerId, userId);
        IPage<Follow> result = followMapper.selectPage(page, queryWrapper);
        if (result.getTotal() == 0) {
            return new Page<>();
        }
        List<Long> followingIds = result.getRecords().stream().map(Follow::getFollowingId).toList();
        Map<Long, UserSimpleDto> followingUserMap = userMapper.selectBatchIds(followingIds)
                .stream()
                .map(user -> UserSimpleDto.builder()
                        .userId(user.getId())
                        .username(user.getUserName())
                        .nickname(user.getNickName())
                        .avatar(user.getAvatar())
                        .isFollow(true)
                        .build())
                .collect(Collectors.toMap(UserSimpleDto::getUserId, userSimpleDto -> userSimpleDto));
        List<UserSimpleDto> followings = followingIds.stream()
                .map(followingId -> followingUserMap.get(followingId))
                .toList();
        Page<UserSimpleDto> pageResult = new Page<>(pageNum, pageSize);
        pageResult.setRecords(followings);
        return pageResult;
    }

    @Override
    public IPage<UserSimpleDto> listFollower(Long userId, int pageNum, int pageSize) {
        Page<Follow> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Follow::getFollowingId, userId);
        IPage<Follow> result = followMapper.selectPage(page, queryWrapper);
        if (result.getTotal() == 0) {
            return new Page<>();
        }
        List<Long> followerIds = result.getRecords().stream().map(Follow::getFollowerId).toList();
        Map<Long, UserSimpleDto> followingUserMap = userMapper.selectBatchIds(followerIds)
                .stream()
                .map(user -> UserSimpleDto.builder()
                        .userId(user.getId())
                        .username(user.getUserName())
                        .nickname(user.getNickName())
                        .avatar(user.getAvatar())
                        .isFollow(true)
                        .build())
                .collect(Collectors.toMap(UserSimpleDto::getUserId, userSimpleDto -> userSimpleDto));
        List<UserSimpleDto> followers = followerIds.stream()
                .map(followingId -> followingUserMap.get(followingId))
                .toList();
        Page<UserSimpleDto> pageResult = new Page<>(pageNum, pageSize);
        pageResult.setRecords(followers);
        return pageResult;
    }
}
