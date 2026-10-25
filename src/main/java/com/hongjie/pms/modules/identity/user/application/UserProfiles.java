package com.hongjie.pms.modules.identity.user.application;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.platform.utils.EmailUtils;
import com.hongjie.pms.modules.community.following.infrastructure.persistence.entity.Follow;
import com.hongjie.pms.modules.community.following.infrastructure.persistence.mapper.FollowMapper;
import com.hongjie.pms.modules.identity.user.api.dto.*;
import com.hongjie.pms.modules.identity.user.api.dto.request.*;
import com.hongjie.pms.modules.identity.user.api.dto.response.UserInfoDto;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.entity.*;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class UserProfiles {
    private final UserMapper users;
    private final UserAvatarCommands avatars;
    private final FollowMapper follows;
    private final UserAssembler assembler;
    private final PasswordEncoder passwords;
    private final UserCache cache;
    private final UserSessions sessions;

    public UserInfoDto info() { return assembler.info(requireUser(actor(), false)); }
    public List<UserSimpleDto> search(String keyword) {
        if (!StringUtils.hasText(keyword)) return List.of();
        return users.selectList(new LambdaQueryWrapper<User>().eq(User::getStatus, 1).eq(User::getSearchable, 1)
                .and(query -> query.like(User::getUserName, keyword).or().like(User::getNickName, keyword))
                .orderByAsc(User::getId).last("LIMIT 10")).stream().map(user -> assembler.simple(user, false)).toList();
    }
    public UserProfileDto profile(Long id) {
        if (id == null || id <= 0) throw new BusinessException(ErrorCode.PARAM_ERROR);
        User user = requireUser(id, false); Long actor = UserContext.getUserId();
        boolean followed = actor != null && follows.exists(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, actor).eq(Follow::getFollowingId, id));
        return assembler.profile(user, followed);
    }
    @Transactional
    public UserInfoDto update(Long id, UserUpdateRequestDto request) {
        own(id);
        if (request == null) throw new BusinessException(ErrorCode.PARAM_ERROR);
        length(request.getNickName(), 50); length(request.getSignature(), 200); length(request.getAvatar(), 500); length(request.getEmail(), 100);
        if (request.getGender() != null && !Set.of(0,1,2).contains(request.getGender())
                || request.getSearchable() != null && !Set.of(0,1).contains(request.getSearchable())
                || StringUtils.hasText(request.getEmail()) && !EmailUtils.isEmail(request.getEmail())
                || request.getTags() != null && (request.getTags().size() > 30 || request.getTags().stream().anyMatch(tag -> tag == null || tag.length() > 50)))
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        User before = requireUser(id, true);
        User patch = new User(); patch.setNickName(request.getNickName()); patch.setSignature(request.getSignature());
        patch.setGender(request.getGender()); patch.setTags(request.getTags()); patch.setPrivacySettings(request.getPrivacySettings());
        patch.setSearchable(request.getSearchable()); patch.setAvatar(request.getAvatar()); patch.setUpdateTime(LocalDateTime.now());
        var where = new LambdaUpdateWrapper<User>().eq(User::getId, id);
        if (request.getEmail() != null) where.set(User::getEmail, StringUtils.hasText(request.getEmail()) ? request.getEmail() : null);
        try { if (users.update(patch, where) != 1) throw new BusinessException(ErrorCode.DB_ERROR); }
        catch (DuplicateKeyException duplicate) { throw new BusinessException(ErrorCode.EMAIL_EXISTS); }
        if (StringUtils.hasText(request.getAvatar()) && !Objects.equals(before.getAvatar(), request.getAvatar())) {
            avatars.remember(id, request.getAvatar());
        }
        cache.afterCommit(id);
        return assembler.info(users.selectById(id));
    }
    @Transactional
    public boolean changePassword(Long id, ChangePasswordRequestDto request) {
        own(id);
        if (request == null || !StringUtils.hasText(request.getOldPassword()) || !StringUtils.hasText(request.getNewPassword()) || request.getNewPassword().length() < 6
                || request.getNewPassword().length() > 20 || !Objects.equals(request.getNewPassword(), request.getConfirmPassword()))
            throw new BusinessException(ErrorCode.PARAM_ERROR, "新密码须为6至20位且与确认密码一致");
        User user = requireUser(id, true);
        if (!passwords.matches(request.getOldPassword(), user.getPassword())) throw new BusinessException(ErrorCode.PASSWORD_ERROR, "旧密码错误");
        if (users.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, id)
                .set(User::getPassword, passwords.encode(request.getNewPassword()))) != 1) throw new BusinessException(ErrorCode.DB_ERROR);
        sessions.passwordChanged(id); cache.afterCommit(id); return true;
    }
    private User requireUser(Long id, boolean lock) {
        var query = new LambdaQueryWrapper<User>().eq(User::getId, id);
        if (lock) query.last("FOR UPDATE");
        User user = users.selectOne(query);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        if (lock && !Objects.equals(user.getStatus(), 1)) throw new BusinessException(ErrorCode.USER_DISABLED);
        return user;
    }
    private Long actor() { Long id = UserContext.getUserId(); if (id == null) throw new BusinessException(ErrorCode.UNAUTHORIZED); return id; }
    private void own(Long id) { if (!Objects.equals(actor(), id)) throw new BusinessException(ErrorCode.FORBIDDEN); }
    private void length(String text, int max) { if (text != null && text.length() > max) throw new BusinessException(ErrorCode.PARAM_ERROR); }
}
