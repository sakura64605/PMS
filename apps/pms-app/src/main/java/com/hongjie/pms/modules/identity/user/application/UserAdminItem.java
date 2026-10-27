package com.hongjie.pms.modules.identity.user.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.scheduling.api.*;
import com.hongjie.pms.modules.scheduling.infrastructure.persistence.entity.*;
import com.hongjie.pms.modules.scheduling.infrastructure.persistence.mapper.*;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.entity.User;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserAdminItem {
    public enum Operation {
        DISABLE,
        ENABLE,
        RESET_PASSWORD
    }

    private final UserMapper users;
    private final DelayTaskMapper tasks;
    private final PasswordEncoder passwords;
    private final UserSessions sessions;
    private final UserCache cache;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(Operation operation, Long id) {
        if (!UserContext.isAdmin()) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (id == null || id <= 0) throw new BusinessException(ErrorCode.PARAM_ERROR);
        User user =
                users.selectOne(
                        new LambdaQueryWrapper<User>().eq(User::getId, id).last("FOR UPDATE"));
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        if (operation == Operation.DISABLE && Objects.equals(id, UserContext.getUserId()))
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能禁用当前登录的管理员账号");
        var change = new LambdaUpdateWrapper<User>().eq(User::getId, id);
        if (operation == Operation.RESET_PASSWORD) {
            change.set(User::getPassword, passwords.encode("123456"));
        } else {
            // Manual enable/disable overrides all earlier timed account-ban jobs, including
            // already-disabled users.
            int cancelled =
                    tasks.delete(
                            new LambdaQueryWrapper<DelayTask>()
                                    .eq(DelayTask::getBusinessId, id)
                                    .eq(DelayTask::getTaskType, "BANNED_PUNISHMENT_END")
                                    .eq(DelayTask::getStatus, 0));
            int target = operation == Operation.DISABLE ? 0 : 1;
            if (Objects.equals(user.getStatus(), target) && cancelled == 0)
                throw new BusinessException(
                        ErrorCode.PARAM_ERROR, target == 0 ? "用户已是禁用状态" : "用户已是启用状态");
            change.set(User::getStatus, target);
        }
        if (users.update(null, change) != 1) throw new BusinessException(ErrorCode.DB_ERROR);
        if (operation == Operation.RESET_PASSWORD) sessions.passwordChanged(id);
        cache.afterCommit(id);
    }
}
