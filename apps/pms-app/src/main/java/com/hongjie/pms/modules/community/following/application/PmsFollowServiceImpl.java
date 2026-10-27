package com.hongjie.pms.modules.community.following.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.community.following.infrastructure.persistence.entity.Follow;
import com.hongjie.pms.modules.community.following.infrastructure.persistence.mapper.FollowMapper;
import com.hongjie.pms.modules.community.following.api.service.FollowService;
import com.hongjie.pms.modules.identity.user.api.dto.UserSimpleDto;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.entity.User;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.UserMapper;
import com.hongjie.pms.shared.core.page.PageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hongjie.pms.platform.cache.DistributedCache;
import com.hongjie.pms.platform.cache.toolkit.CacheUtil;
import com.hongjie.pms.shared.core.cache.AfterCommitCache;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Primary
@RequiredArgsConstructor
public class PmsFollowServiceImpl implements FollowService {
    private final DistributedCache cache;
    private final FollowMapper follows;
    private final UserMapper users;
    private final FollowWriteMapper writes;
    private final FollowInboxWriter inbox;
    private final ApplicationEventPublisher events;

    /** Keep the existing toggle endpoint: each successful request flips this directed edge. */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void followUser(Long actor, Long target) {
        Long current = currentUser();
        validId(actor);
        validId(target);
        if (!current.equals(actor)) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (actor.equals(target)) throw new BusinessException(ErrorCode.PARAM_ERROR, "不能关注自己");
        Long low = Math.min(actor, target), high = Math.max(actor, target);
        User first = writes.lockUser(low), second = writes.lockUser(high);
        if (first == null || second == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        User author = target.equals(low) ? first : second;
        Long relation = writes.relation(actor, target);
        if (relation != null) {
            changed(writes.delete(relation, actor, target));
            changed(writes.followingCount(actor, -1));
            changed(writes.followerCount(target, -1));
            inbox.remove(actor, target);
        } else {
            User follower = actor.equals(low) ? first : second;
            if (!Integer.valueOf(1).equals(follower.getStatus())
                    || !Integer.valueOf(1).equals(author.getStatus()))
                throw new BusinessException(ErrorCode.FORBIDDEN, "禁用用户不能建立关注关系");
            changed(writes.insert(actor, target));
            changed(writes.followingCount(actor, 1));
            changed(writes.followerCount(target, 1));
            // Load full author once for the inbox projection; locked rows contain identity/status
            // only.
            User profile = users.selectById(target);
            if (profile == null) throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
            inbox.populate(actor, profile);
            events.publishEvent(new FollowCreated(actor, target, follower.getUserName()));
        }
        AfterCommitCache.evict(() -> cache.delete(CacheUtil.buildKey("user", actor.toString())));
        AfterCommitCache.evict(() -> cache.delete(CacheUtil.buildKey("user", target.toString())));
    }

    @Override
    public IPage<UserSimpleDto> listFollowing(Long userId, int pageNum, int pageSize) {
        return list(userId, pageNum, pageSize, true);
    }

    @Override
    public IPage<UserSimpleDto> listFollower(Long userId, int pageNum, int pageSize) {
        return list(userId, pageNum, pageSize, false);
    }

    private IPage<UserSimpleDto> list(Long owner, int number, int size, boolean following) {
        Long viewer = currentUser();
        validId(owner);
        var query = new LambdaQueryWrapper<Follow>();
        if (following) query.eq(Follow::getFollowerId, owner);
        else query.eq(Follow::getFollowingId, owner);
        query.orderByDesc(Follow::getCreateTime).orderByDesc(Follow::getId);
        IPage<Follow> page =
                follows.selectPage(PageQuery.of((long) number, (long) size).toPage(), query);
        var result = new Page<UserSimpleDto>(page.getCurrent(), page.getSize(), page.getTotal());
        List<Long> ids =
                page.getRecords().stream()
                        .map(row -> following ? row.getFollowingId() : row.getFollowerId())
                        .toList();
        if (ids.isEmpty()) {
            result.setRecords(List.of());
            return result;
        }
        Map<Long, User> profiles =
                users.selectBatchIds(ids).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
        Set<Long> followed =
                following && owner.equals(viewer)
                        ? new HashSet<>(ids)
                        : new HashSet<>(follows.selectFollowedIds(viewer, ids));
        result.setRecords(
                ids.stream()
                        .map(profiles::get)
                        .filter(Objects::nonNull)
                        .map(
                                user ->
                                        UserSimpleDto.builder()
                                                .userId(user.getId())
                                                .username(user.getUserName())
                                                .nickname(user.getNickName())
                                                .avatar(user.getAvatar())
                                                .isFollow(followed.contains(user.getId()))
                                                .build())
                        .toList());
        return result;
    }

    private Long currentUser() {
        Long id = UserContext.getUserId();
        if (id == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return id;
    }

    private void validId(Long id) {
        if (id == null || id <= 0) throw new BusinessException(ErrorCode.PARAM_ERROR);
    }

    private void changed(int rows) {
        if (rows != 1) throw new BusinessException(ErrorCode.PARAM_ERROR, "关注关系发生变化，请重试");
    }
}
