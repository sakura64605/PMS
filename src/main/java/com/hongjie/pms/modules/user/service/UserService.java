package com.hongjie.pms.modules.user.service;

import com.hongjie.pms.modules.user.dto.UserSimpleDto;
import com.hongjie.pms.modules.user.dto.request.ChangePasswordRequestDto;
import com.hongjie.pms.modules.user.dto.request.LoginRequestDto;
import com.hongjie.pms.modules.user.dto.request.RegisterRequestDto;
import com.hongjie.pms.modules.user.dto.request.UserUpdateRequestDto;
import com.hongjie.pms.modules.user.dto.response.LoginResponseDto;
import com.hongjie.pms.modules.user.dto.response.RegisterResponseDto;
import com.hongjie.pms.modules.user.dto.response.UserInfoDto;
import com.hongjie.pms.modules.user.dto.UserProfileDto;
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
