package com.hongjie.pms.modules.identity.user.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.entity.*;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserAvatarCommands {
    private final UserMapper users;
    private final AvatarHistoryMapper history;
    private final UserCache cache;

    public Long actor() {
        Long id = UserContext.getUserId();
        if (id == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return id;
    }

    @Transactional
    public void uploaded(Long id, String url) {
        User user = lock(id);
        if (url == null || url.isBlank() || url.length() > 500)
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        if (users.update(
                        null,
                        new LambdaUpdateWrapper<User>()
                                .eq(User::getId, id)
                                .set(User::getAvatar, url))
                != 1) throw new BusinessException(ErrorCode.DB_ERROR);
        remember(id, url);
        cache.afterCommit(id);
    }

    @Transactional
    public String switchTo(Long historyId) {
        Long id = actor();
        lock(id);
        AvatarHistory row = history.selectById(historyId);
        if (row == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        if (!id.equals(row.getUserId())) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (users.update(
                        null,
                        new LambdaUpdateWrapper<User>()
                                .eq(User::getId, id)
                                .set(User::getAvatar, row.getAvatarUrl()))
                != 1) throw new BusinessException(ErrorCode.DB_ERROR);
        history.update(
                null,
                new LambdaUpdateWrapper<AvatarHistory>()
                        .eq(AvatarHistory::getId, historyId)
                        .set(AvatarHistory::getUpdateTime, LocalDateTime.now()));
        cache.afterCommit(id);
        return row.getAvatarUrl();
    }

    public List<AvatarHistory> history() {
        return history.selectList(
                new LambdaQueryWrapper<AvatarHistory>()
                        .eq(AvatarHistory::getUserId, actor())
                        .orderByDesc(AvatarHistory::getUpdateTime)
                        .orderByDesc(AvatarHistory::getId)
                        .last("LIMIT 11"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void remember(Long id, String url) {
        AvatarHistory record = new AvatarHistory();
        record.setUserId(id);
        record.setAvatarUrl(url);
        record.setUpdateTime(LocalDateTime.now());
        history.insert(record);
        var rows =
                history.selectList(
                        new LambdaQueryWrapper<AvatarHistory>()
                                .eq(AvatarHistory::getUserId, id)
                                .orderByDesc(AvatarHistory::getUpdateTime)
                                .orderByDesc(AvatarHistory::getId));
        if (rows.size() > 11)
            history.deleteBatchIds(
                    rows.subList(11, rows.size()).stream().map(AvatarHistory::getId).toList());
        // URLs can also be referenced by posts: pruning history must not delete the underlying OSS
        // objects.
    }

    private User lock(Long id) {
        if (!Objects.equals(actor(), id)) throw new BusinessException(ErrorCode.FORBIDDEN);
        User user =
                users.selectOne(
                        new LambdaQueryWrapper<User>().eq(User::getId, id).last("FOR UPDATE"));
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        if (!Objects.equals(user.getStatus(), 1))
            throw new BusinessException(ErrorCode.USER_DISABLED);
        return user;
    }
}
