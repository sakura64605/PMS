package com.hongjie.pms.modules.identity.user.application;
import com.hongjie.pms.modules.identity.user.api.dto.*;
import com.hongjie.pms.modules.identity.user.api.dto.response.UserInfoDto;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.entity.User;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class UserAssembler {
    public UserInfoDto info(User user) {
        return UserInfoDto.builder().userId(user.getId()).userName(user.getUserName()).nickName(user.getNickName())
                .avatar(user.getAvatar()).phone(user.getPhone()).email(user.getEmail()).role(user.getRole())
                .status(user.getStatus()).signature(user.getSignature()).gender(user.getGender()).tags(user.getTags())
                .privacySettings(user.getPrivacySettings()).searchable(user.getSearchable()).createTime(user.getCreateTime())
                .followerCount(user.getFollowerCount()).followingCount(user.getFollowingCount()).likeCount(user.getLikeCount())
                .password(null).build();
    }
    public UserSimpleDto simple(User user, boolean followed) {
        return UserSimpleDto.builder().userId(user.getId()).username(user.getUserName()).nickname(user.getNickName())
                .avatar(user.getAvatar()).isFollow(followed).build();
    }
    public UserProfileDto profile(User user, boolean followed) {
        var result = new UserProfileDto(); result.setUser(simple(user, followed));
        Map<String, Boolean> privacy = user.getPrivacySettings() == null ? Map.of() : user.getPrivacySettings();
        if (Boolean.TRUE.equals(privacy.get("phone"))) result.setPhone(user.getPhone());
        if (Boolean.TRUE.equals(privacy.get("email"))) result.setEmail(user.getEmail());
        if (Boolean.TRUE.equals(privacy.get("tags"))) result.setTags(user.getTags());
        result.setSignature(user.getSignature()); result.setGender(user.getGender()); result.setJoinTime(user.getCreateTime());
        result.setLastActiveTime(user.getLastActiveTime()); result.setFollowerCount(user.getFollowerCount());
        result.setFollowingCount(user.getFollowingCount()); result.setLikeCount(user.getLikeCount());
        return result;
    }
}
