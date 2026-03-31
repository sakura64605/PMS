package com.hongjie.pms.modules.following.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.modules.user.dto.UserSimpleDto;

public interface FollowService {
    void followUser(Long currentUserId, Long userId);

    IPage<UserSimpleDto> listFollowing(Long userId, int pageNum, int pageSize);

    IPage<UserSimpleDto> listFollower(Long userId, int pageNum, int pageSize);
}
