package com.hongjie.pms.modules.activity.application;

import com.hongjie.pms.modules.activity.api.dto.response.*;
import com.hongjie.pms.modules.activity.infrastructure.persistence.entity.*;
import com.hongjie.pms.modules.identity.user.api.dto.UserSimpleDto;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.entity.User;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ActivityDtoAssembler {
    private final UserMapper users;

    public Map<Long, UserSimpleDto> users(Collection<Long> ids) {
        Set<Long> unique = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (unique.isEmpty()) return Map.of();
        return users.selectBatchIds(unique).stream().collect(Collectors.toMap(User::getId,
                row -> UserSimpleDto.builder().userId(row.getId()).username(row.getUserName())
                        .nickname(row.getNickName()).avatar(row.getAvatar()).build(), (first, second) -> first));
    }

    public UserSimpleDto user(Map<Long, UserSimpleDto> rows, Long id) {
        return rows.getOrDefault(id, UserSimpleDto.builder().userId(id).username("已注销").nickname("已注销").build());
    }

    public ActivityListRespDto list(Activity row, UserSimpleDto user, boolean signedUp) {
        return ActivityListRespDto.builder().id(row.getId()).user(user).title(row.getTitle()).content(row.getContent())
                .images(row.getImages() == null || row.getImages().isEmpty() ? null : row.getImages().get(0))
                .location(row.getLocation()).maxPeople(row.getMaxPeople()).currentPeople(row.getCurrentPeople())
                .status(row.getStatus()).auditStatus(row.getAuditStatus()).startTime(row.getStartTime())
                .endTime(row.getEndTime()).createTime(row.getCreateTime()).viewCount(row.getViewCount())
                .likeCount(row.getLikeCount()).commentCount(row.getCommentCount()).isSignedUp(signedUp ? 1 : 0).build();
    }

    public ActivityDetailRespDto detail(Activity row, UserSimpleDto user, boolean signedUp, boolean liked) {
        ActivityDetailResponse result = new ActivityDetailResponse();
        result.setId(row.getId()); result.setUser(user); result.setTitle(row.getTitle()); result.setContent(row.getContent());
        result.setImages(row.getImages()); result.setLocation(row.getLocation()); result.setMaxPeople(row.getMaxPeople());
        result.setCurrentPeople(row.getCurrentPeople()); result.setStartTime(row.getStartTime()); result.setEndTime(row.getEndTime());
        result.setStatus(row.getStatus()); result.setAuditStatus(row.getAuditStatus()); result.setViewCount(row.getViewCount());
        result.setLikeCount(row.getLikeCount()); result.setCommentCount(row.getCommentCount()); result.setShareCount(row.getShareCount());
        result.setCreateTime(row.getCreateTime()); result.setUpdateTime(row.getUpdateTime()); result.setDeleted(row.getDeleted());
        result.setIsSignUp(signedUp ? 1 : 0); result.setIsLike(liked ? 1 : 0);
        return result;
    }

    public SignUpResponse signup(ActivitySignup row, UserSimpleDto user) {
        return SignUpResponse.builder().signupId(row.getId()).user(user).realName(row.getRealName())
                .phone(row.getPhone()).remark(row.getRemark()).status(row.getStatus()).signupTime(row.getCreateTime())
                .isCheckedIn(row.getCheckInTime() != null).build();
    }
}
