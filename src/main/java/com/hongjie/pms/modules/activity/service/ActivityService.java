package com.hongjie.pms.modules.activity.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.modules.activity.dto.request.ActivityListRequestDto;
import com.hongjie.pms.modules.activity.dto.request.SignUpInfoRequest;
import com.hongjie.pms.modules.activity.dto.response.ActivityDetailRespDto;
import com.hongjie.pms.modules.activity.dto.response.ActivityListRespDto;
import com.hongjie.pms.modules.activity.dto.request.ActivityRequestDto;
import com.hongjie.pms.modules.activity.dto.response.ActivityPostRespDto;
import com.hongjie.pms.modules.activity.dto.response.SignUpResponse;
import jakarta.validation.Valid;

import java.util.List;

public interface ActivityService {
    ActivityPostRespDto postActivity(@Valid ActivityRequestDto activityRequestDto);

    void updateActivity(@Valid ActivityRequestDto activityRequestDto);

    void deleteActivity(Long id);

    IPage<ActivityListRespDto> getActivityList(ActivityListRequestDto activityListRequestDto);

    ActivityDetailRespDto getActivityDetail(Long id);

    void signUp(@Valid SignUpInfoRequest signUpInfoRequest);

    void cancelSignUp(Long id);

    IPage<ActivityListRespDto> getRecycleBinList(ActivityListRequestDto request);

    void recoverActivity(Long id);

    IPage<SignUpResponse> getSignUpList(Long id, int pageNum, int pageSize);

    void signIn(Long activityId, Long userId);
}
