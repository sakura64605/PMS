package com.hongjie.pms.refactor.activity;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.modules.activity.dto.request.*;
import com.hongjie.pms.modules.activity.dto.response.*;
import com.hongjie.pms.modules.activity.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/** The only primary activity facade; every operation delegates to the new implementation. */
@Service
@Primary
@RequiredArgsConstructor
public class PmsActivityServiceImpl implements ActivityService {
    private final ActivityContentCommands content;
    private final ActivityParticipationCommands participation;
    private final ActivityLifecycleCommands lifecycle;
    private final ActivityQueries queries;
    @Override public ActivityPostRespDto postActivity(ActivityRequestDto request) { return content.publish(request); }
    @Override public void updateActivity(ActivityRequestDto request) { content.edit(request); }
    @Override public void deleteActivity(Long id) { lifecycle.delete(id); }
    @Override public void recoverActivity(Long id) { lifecycle.restore(id); }
    @Override public void signUp(SignUpInfoRequest request) { participation.signUp(request); }
    @Override public void cancelSignUp(Long id) { participation.cancel(id); }
    @Override public void signIn(Long activityId, Long userId) { participation.signIn(activityId, userId); }
    @Override public IPage<ActivityListRespDto> getActivityList(ActivityListRequestDto request) { return queries.list(request); }
    @Override public IPage<ActivityListRespDto> getRecycleBinList(ActivityListRequestDto request) { return queries.recycle(request); }
    @Override public ActivityDetailRespDto getActivityDetail(Long id) { return queries.detail(id); }
    @Override public IPage<SignUpResponse> getSignUpList(Long id, int page, int size) { return queries.signupList(id, page, size); }
}
