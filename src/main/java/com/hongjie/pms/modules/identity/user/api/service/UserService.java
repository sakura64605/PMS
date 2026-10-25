package com.hongjie.pms.modules.identity.user.api.service;

import com.hongjie.pms.modules.identity.user.api.dto.UserSimpleDto;
import com.hongjie.pms.modules.identity.user.api.dto.request.ChangePasswordRequestDto;
import com.hongjie.pms.modules.identity.user.api.dto.request.LoginRequestDto;
import com.hongjie.pms.modules.identity.user.api.dto.request.RegisterRequestDto;
import com.hongjie.pms.modules.identity.user.api.dto.request.UserUpdateRequestDto;
import com.hongjie.pms.modules.identity.user.api.dto.response.LoginResponseDto;
import com.hongjie.pms.modules.identity.user.api.dto.response.RegisterResponseDto;
import com.hongjie.pms.modules.identity.user.api.dto.response.UserInfoDto;
import com.hongjie.pms.modules.identity.user.api.dto.UserProfileDto;
import jakarta.validation.Valid;

import java.util.List;

public interface UserService {

    LoginResponseDto login(LoginRequestDto loginRequestDto);

    RegisterResponseDto register(@Valid RegisterRequestDto registerRequestDto);

    UserInfoDto getUserInfo();

    UserInfoDto updateUserInfo(Long userId, @Valid UserUpdateRequestDto updateDto);

    boolean changePassword(Long userId, @Valid ChangePasswordRequestDto changePasswordRequestDto);

    List<UserSimpleDto> searchUsers(String keyword);

    UserProfileDto getUserProfileInfo(Long userId);
}
