package com.hongjie.pms.modules.user.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hongjie.pms.common.annotation.CircuitBreaker;
import com.hongjie.pms.common.annotation.DistributedCacheable;
import com.hongjie.pms.common.base.core.UpdateTimeContext;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.exception.SystemException;
import com.hongjie.pms.common.handler.MyMetaObjectHandler;
import com.hongjie.pms.common.utils.AccountUtils;
import com.hongjie.pms.common.utils.EmailUtils;
import com.hongjie.pms.common.utils.JWTUtils;
import com.hongjie.pms.common.utils.PasswordUtils;
import com.hongjie.pms.modules.following.entity.Follow;
import com.hongjie.pms.modules.following.mapper.FollowMapper;
import com.hongjie.pms.modules.user.dto.UserSimpleDto;
import com.hongjie.pms.modules.user.dto.request.ChangePasswordRequestDto;
import com.hongjie.pms.modules.user.dto.request.LoginRequestDto;
import com.hongjie.pms.modules.user.dto.request.RegisterRequestDto;
import com.hongjie.pms.modules.user.dto.request.UserUpdateRequestDto;
import com.hongjie.pms.modules.user.dto.response.LoginResponseDto;
import com.hongjie.pms.modules.user.dto.response.RegisterResponseDto;
import com.hongjie.pms.modules.user.dto.response.UserInfoDto;
import com.hongjie.pms.modules.user.entity.AvatarHistory;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.dto.UserProfileDto;
import com.hongjie.pms.modules.user.mapper.AvatarHistoryMapper;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.modules.user.service.UserService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final JWTUtils jwtUtils;
    private final AvatarHistoryMapper avatarHistoryMapper;
    private final FollowMapper followMapper;

    @Override
    public LoginResponseDto login(LoginRequestDto loginRequestDto) {
        User user = findUserByAccount(loginRequestDto.getAccount());

        // 1. 检查用户是否存在
        if (user == null) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR, "用户名或密码错误");  // 统一提示，避免枚举用户名
        }

        // 2. 检查用户状态
        if (user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        user.setLastActiveTime(LocalDateTime.now());

        String password = loginRequestDto.getPassword();
        if(!PasswordUtils.matches(password, user.getPassword())){
            throw new BusinessException(ErrorCode.PASSWORD_ERROR, "用户名或密码错误");
        }

        String token = jwtUtils.generateToken(user.getUserName(), user.getRole(), user.getId());

        log.info("用户{}登录成功", user.getUserName());

        return LoginResponseDto.builder()
                .token(token)                    // 返回token
                .tokenPrefix(jwtUtils.getTokenPrefix()) // token前缀（Bearer）
                .expiresIn(jwtUtils.getExpire())  // 过期时间
                .username(user.getUserName())
                .nickname(user.getNickName())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .build();
    }

    @Override
    public RegisterResponseDto register(RegisterRequestDto registerRequestDto) {

        String userName = registerRequestDto.getUserName();
        String nickName = registerRequestDto.getNickName();
        String password = registerRequestDto.getPassword();
        String phone = registerRequestDto.getPhone();

        if (findUserByUsername(userName) != null) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        if (findUserByPhone(phone) != null) {
            throw new BusinessException(ErrorCode.PHONE_EXISTS);
        }
        if (password.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "密码不能为空");
        }
        if (password.length() < 6) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "密码长度不能小于6");
        }

        if(nickName == null || nickName.isBlank()){
            nickName = userName;
        }

        String encryptPassword = PasswordUtils.encrypt(password);

        User user = new User();
        user.setUserName(userName);
        user.setNickName(nickName);
        user.setPassword(encryptPassword);
        user.setPhone(phone);
        user.setStatus(1);
        user.setRole(0);

        Integer result = userMapper.insert(user);
        String token = jwtUtils.generateToken(userName, 0, user.getId());
        if(result > 0){
            log.info("用户{}注册成功", userName);
            return RegisterResponseDto.builder()
                    .token(token)
                    .tokenPrefix(jwtUtils.getTokenPrefix())
                    .expiresIn(jwtUtils.getExpire())
                    .userId(user.getId())
                    .userName(userName)
                    .nickName(nickName)
                    .phone(phone)
                    .build();
        }else{
            throw new SystemException(ErrorCode.DB_ERROR);
        }
    }

    @Override
    public UserInfoDto getUserInfo() {
        Long userId = UserContext.getUserId();
        String userName = UserContext.getUserName();
        User user = findUserByUsername(userName);

        return UserInfoDto.builder()
                .userId(userId)
                .userName(userName)
                .nickName(user.getNickName())
                .avatar(user.getAvatar())
                .phone(user.getPhone())
                .role(user.getRole())
                .signature(user.getSignature())
                .tags(user.getTags())
                .privacySettings(user.getPrivacySettings())
                .gender(user.getGender())
                .status(user.getStatus())
                .createTime(user.getCreateTime())
                .email(user.getEmail())
                .password(user.getPassword())
                .searchable(user.getSearchable())
                .followerCount(user.getFollowerCount())
                .followingCount(user.getFollowingCount())
                .likeCount(user.getLikeCount())
                .build();
    }

    @Override
    @CacheEvict(value = "user", key = "#userId")
    public UserInfoDto updateUserInfo(Long userId, UserUpdateRequestDto updateDto) {

        User user = new User();
        user.setId(userId);
        user.setNickName(updateDto.getNickName());
        user.setAvatar(updateDto.getAvatar());
        user.setGender(updateDto.getGender());
        user.setSignature(updateDto.getSignature());
        user.setTags(updateDto.getTags());
        user.setPrivacySettings(updateDto.getPrivacySettings());
        user.setSearchable(updateDto.getSearchable());

        String oldAvatar = avatarHistoryMapper.selectById(userId).getAvatarUrl();
        String newAvatar = updateDto.getAvatar();

        if(!oldAvatar.equals(newAvatar)){
            AvatarHistory avatarHistory = new AvatarHistory();

            avatarHistory.setUserId(userId);
            avatarHistory.setAvatarUrl(updateDto.getAvatar());
            avatarHistory.setUpdateTime(LocalDateTime.now());

            avatarHistoryMapper.updateById(avatarHistory);
        }

        if(EmailUtils.isEmail(updateDto.getEmail()) && updateDto.getEmail() != null) {
            user.setEmail(updateDto.getEmail());
        } else {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "邮箱格式错误");
        }

        Integer result = userMapper.updateById(user);
        if(result > 0){
            return UserInfoDto.builder()
                    .userId(userId)
                    .nickName(updateDto.getNickName())
                    .avatar(updateDto.getAvatar())
                    .gender(updateDto.getGender())
                    .signature(updateDto.getSignature())
                    .tags(updateDto.getTags())
                    .privacySettings(updateDto.getPrivacySettings())
                    .email(updateDto.getEmail())
                    .searchable(updateDto.getSearchable())
                    .build();
        }
        return null;
    }

    @Override
    public boolean changePassword(Long userId, ChangePasswordRequestDto changePasswordRequestDto) {

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", userId);

        User user = userMapper.selectOne(queryWrapper);

        String oldPassword = changePasswordRequestDto.getOldPassword();
        String newPassword = changePasswordRequestDto.getNewPassword();
        String confirmPassword = changePasswordRequestDto.getConfirmPassword();

        if (!PasswordUtils.matches(oldPassword, user.getPassword(), user.getUserName())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR, "旧密码错误");
        }

        if (!newPassword.equals(confirmPassword)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "新密码和确认密码不一致");
        }
        user.setPassword(PasswordUtils.encrypt(newPassword));
        boolean result = userMapper.updateById(user) > 0;
        if (result) {
            return true;
        } else {
            throw new BusinessException(ErrorCode.DB_ERROR, "修改密码失败");
        }
    }

    @CircuitBreaker(
            value = "searchUsers",
            windowSize = 10,
            minRequestAmount = 5,
            errorRateThreshold = 0.5,
            openDurationSeconds = 10,
            fallbackMethod = "fallbackSearchUsers"
    )
    @Override
    public List<UserSimpleDto> searchUsers(String keyword) {
        // 1. 关键词不能为空
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }

        // 2. 构建查询条件
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w
                .like(User::getUserName, keyword)   // 按用户名搜索
                .or()
                .like(User::getNickName, keyword)   // 按昵称搜索
        );
        wrapper.eq(User::getStatus, 1);              // 只查询正常状态的用户
        wrapper.eq(User::getSearchable, 1);

        // 3. 限制返回数量（避免返回太多数据）
        wrapper.last("limit 10");

        // 4. 执行查询
        List<User> users = userMapper.selectList(wrapper);

        log.info("搜索用户: keyword={}, 结果数量={}", keyword, users.size());

        // 5. 转换为 DTO
        return users.stream()
                .map(user -> UserSimpleDto.builder()
                        .userId(user.getId())
                        .username(user.getUserName())
                        .nickname(user.getNickName())
                        .avatar(user.getAvatar())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 降级方法：返回空列表
     */
    public List<UserSimpleDto> fallbackSearchUsers(String keyword) {
        log.warn("搜索用户熔断降级: keyword={}", keyword);
        return new ArrayList<>();
    }

    public List<UserSimpleDto> fallbackSearchUsers(String keyword, Exception e) {
        log.error("搜索用户熔断降级: keyword={}, error={}", keyword, e.getMessage());
        return new ArrayList<>();
    }

    @CircuitBreaker(
            value = "getUserById",
            windowSize = 10,
            minRequestAmount = 5,
            errorRateThreshold = 0.5,
            openDurationSeconds = 10,
            fallbackMethod = "fallbackGetUserById"
    )
    @Override
    @DistributedCacheable(
            value = "user",
            key = "#userId",
            ttl = 3600,
            bloomFilter = true   // 开启布隆过滤器
    )
    public UserProfileDto getUserProfileInfo(Long userId) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", userId);

        User user = userMapper.selectOne(queryWrapper);

        UserProfileDto userProfileDto = new UserProfileDto();

        Map<String, Boolean> privacySettings = user.getPrivacySettings();

        if(privacySettings.get("phone")){
            userProfileDto.setPhone(user.getPhone());
        }
        if(privacySettings.get("email")){
            userProfileDto.setEmail(user.getEmail());
        }
        if(privacySettings.get("tags")){
            userProfileDto.setTags(user.getTags());
        }

        Long currentUserId = UserContext.getUserId();

        LambdaQueryWrapper<Follow> followWrapper = new LambdaQueryWrapper<>();
        followWrapper.eq(Follow::getFollowingId, userId);
        followWrapper.eq(Follow::getFollowerId, currentUserId);
        Follow follow = followMapper.selectOne(followWrapper);
        if(follow != null){
            userProfileDto.setUser(UserSimpleDto.builder()
                    .userId(user.getId())
                    .username(user.getUserName())
                    .nickname(user.getNickName())
                    .avatar(user.getAvatar())
                    .isFollow(true)
                    .build());
        } else {
            userProfileDto.setUser(UserSimpleDto.builder()
                    .userId(user.getId())
                    .username(user.getUserName())
                    .nickname(user.getNickName())
                    .avatar(user.getAvatar())
                    .build());
        }

        userProfileDto.setSignature(user.getSignature());
        userProfileDto.setFollowerCount(user.getFollowerCount());
        userProfileDto.setFollowingCount(user.getFollowingCount());
        userProfileDto.setLikeCount(user.getLikeCount());
        userProfileDto.setJoinTime(user.getCreateTime());
        userProfileDto.setLastActiveTime(user.getLastActiveTime());
        userProfileDto.setGender(user.getGender());
        return userProfileDto;
    }

    /**
     * 降级方法
     */
    public User fallbackGetUserById(Long userId) {
        return fallbackGetUserById(userId, null);
    }

    public User fallbackGetUserById(Long userId, Exception e) {
        log.error("用户查询熔断降级: userId={}, error={}", userId, e.getMessage());

        // 返回一个特殊标记，让前端知道是系统繁忙
        User fallback = new User();
        fallback.setId(userId);
        fallback.setUserName(null);
        fallback.setNickName("系统繁忙，请稍后再试");
        fallback.setStatus(-1);  // 特殊状态码，前端判断后显示提示
        return fallback;
    }

    private User findUserByAccount(@NotBlank(message = "账号不能为空") String account) {
        if(AccountUtils.isPhone(account)){
            return findUserByPhone(account);
        }else{
            return findUserByUsername(account);
        }
    }

    private User findUserByUsername(@NotBlank(message = "用户名不能为空") String username) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUserName, username);
        return userMapper.selectOne(queryWrapper);
    }

    private User findUserByPhone(@NotBlank(message = "手机号不能为空") String phone) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getPhone, phone);
        return userMapper.selectOne(queryWrapper);
    }
}
