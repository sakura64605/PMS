package com.hongjie.pms.refactor.user;
import com.hongjie.pms.modules.user.service.UserService;
import com.hongjie.pms.modules.user.dto.*;
import com.hongjie.pms.modules.user.dto.request.*;
import com.hongjie.pms.modules.user.dto.response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import java.util.List;

@Service @Primary @RequiredArgsConstructor
public class PmsUserServiceImpl implements UserService {
    private final UserAuthentication authentication;
    private final UserProfiles profiles;
    @Override public LoginResponseDto login(LoginRequestDto request) { return authentication.login(request); }
    @Override public RegisterResponseDto register(RegisterRequestDto request) { return authentication.register(request); }
    @Override public UserInfoDto getUserInfo() { return profiles.info(); }
    @Override public UserInfoDto updateUserInfo(Long id, UserUpdateRequestDto request) { return profiles.update(id, request); }
    @Override public boolean changePassword(Long id, ChangePasswordRequestDto request) { return profiles.changePassword(id, request); }
    @Override public List<UserSimpleDto> searchUsers(String keyword) { return profiles.search(keyword); }
    @Override public UserProfileDto getUserProfileInfo(Long id) { return profiles.profile(id); }
}
