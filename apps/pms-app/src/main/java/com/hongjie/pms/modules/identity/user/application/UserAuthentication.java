package com.hongjie.pms.modules.identity.user.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.platform.utils.*;
import com.hongjie.pms.modules.identity.user.api.dto.request.*;
import com.hongjie.pms.modules.identity.user.api.dto.response.*;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.entity.User;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.UserMapper;
import com.hongjie.pms.shared.core.cache.AfterCommitCache;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserAuthentication {
    private final UserMapper users;
    private final PasswordEncoder passwords;
    private final JWTUtils jwt;
    private final UserSessions sessions;
    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;

    @Transactional
    public LoginResponseDto login(LoginRequestDto request) {
        if (request == null
                || !StringUtils.hasText(request.getAccount())
                || !StringUtils.hasText(request.getPassword()))
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        var query = new LambdaQueryWrapper<User>();
        if (AccountUtils.isPhone(request.getAccount()))
            query.eq(User::getPhone, request.getAccount());
        else query.eq(User::getUserName, request.getAccount());
        User user = users.selectOne(query);
        if (user == null || !passwords.matches(request.getPassword(), user.getPassword()))
            throw new BusinessException(ErrorCode.PASSWORD_ERROR, "用户名或密码错误");
        if (!Objects.equals(user.getStatus(), 1))
            throw new BusinessException(ErrorCode.USER_DISABLED);
        if (users.update(
                        null,
                        new LambdaUpdateWrapper<User>()
                                .eq(User::getId, user.getId())
                                .eq(User::getStatus, 1)
                                .eq(User::getPassword, user.getPassword())
                                .set(User::getLastActiveTime, LocalDateTime.now()))
                != 1) throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号状态已改变，请重新登录");
        return LoginResponseDto.builder()
                .token(sessions.issue(user))
                .tokenPrefix(jwt.getTokenPrefix())
                .expiresIn(jwt.getExpire())
                .username(user.getUserName())
                .nickname(user.getNickName())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .build();
    }

    @Transactional
    public RegisterResponseDto register(RegisterRequestDto request) {
        if (request == null
                || !StringUtils.hasText(request.getUserName())
                || request.getUserName().length() > 50
                || !StringUtils.hasText(request.getPhone())
                || !AccountUtils.isPhone(request.getPhone())
                || !StringUtils.hasText(request.getPassword())
                || request.getPassword().length() < 6
                || request.getPassword().length() > 20
                || request.getNickName() != null && request.getNickName().length() > 50)
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请检查用户名、手机号和密码（6至20位）");
        String name = request.getUserName();
        if (users.exists(new LambdaQueryWrapper<User>().eq(User::getUserName, name)))
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        if (users.exists(new LambdaQueryWrapper<User>().eq(User::getPhone, request.getPhone())))
            throw new BusinessException(ErrorCode.PHONE_EXISTS);
        User user = new User();
        user.setUserName(name);
        user.setNickName(StringUtils.hasText(request.getNickName()) ? request.getNickName() : name);
        user.setPassword(passwords.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setRole(0);
        user.setStatus(1);
        user.setSearchable(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        try {
            if (users.insert(user) != 1) throw new BusinessException(ErrorCode.DB_ERROR);
        } catch (DuplicateKeyException conflict) {
            if (users.selectOne(
                            new LambdaQueryWrapper<User>()
                                    .eq(User::getUserName, name)
                                    .last("FOR UPDATE"))
                    != null) throw new BusinessException(ErrorCode.USERNAME_EXISTS);
            throw new BusinessException(ErrorCode.PHONE_EXISTS);
        }
        AfterCommitCache.evict(
                () -> {
                    userRegisterCachePenetrationBloomFilter.add("username:" + name);
                    userRegisterCachePenetrationBloomFilter.add("phone:" + request.getPhone());
                });
        return RegisterResponseDto.builder()
                .userId(user.getId())
                .userName(name)
                .nickName(user.getNickName())
                .phone(user.getPhone())
                .token(sessions.issue(user))
                .tokenPrefix(jwt.getTokenPrefix())
                .expiresIn(jwt.getExpire())
                .build();
    }
}
