package com.hongjie.pms.refactor.activity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.activity.cache.ActivityCache;
import com.hongjie.pms.modules.activity.dto.request.SignUpInfoRequest;
import com.hongjie.pms.modules.activity.entity.*;
import com.hongjie.pms.modules.activity.mapper.ActivitySignupMapper;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.refactor.core.enums.biz.SignupStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.Objects;

/** Lock order for all participation writes: activity, then its signup row. */
@Service
@RequiredArgsConstructor
public class ActivityParticipationCommands {
    private final ActivityParticipationMapper activities;
    private final ActivitySignupMapper signups;
    private final UserMapper users;
    private final ActivityCache cache;
    private final ApplicationEventPublisher events;

    @Transactional
    public void signUp(SignUpInfoRequest request) {
        Long actor=actor();
        if(request==null || !StringUtils.hasText(request.getRealName()) || !StringUtils.hasText(request.getPhone()))
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        Activity activity=lock(request.getActivityId());
        LocalDateTime now=LocalDateTime.now();
        User user=users.selectById(actor);
        if(user==null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        if(!Objects.equals(user.getStatus(),1)) throw new BusinessException(ErrorCode.USER_DISABLED);
        if(com.hongjie.pms.refactor.user.UserRestrictions.signupBlocked(user,now))
            throw new BusinessException(ErrorCode.FORBIDDEN,"当前账号被限制报名");
        if(Objects.equals(activity.getUserId(),actor)) throw new BusinessException(ErrorCode.PARAM_ERROR,"不能报名自己的活动");
        requireOpen(activity,now);
        if(signup(activity.getId(),actor)!=null) throw new BusinessException(ErrorCode.ACTIVITY_SIGNUP_EXISTS);
        if(activities.reserve(activity.getId(),now)!=1) throw new BusinessException(ErrorCode.ACTIVITY_FULL);
        ActivitySignup signup=new ActivitySignup();
        signup.setActivityId(activity.getId()); signup.setUserId(actor);
        signup.setRealName(request.getRealName()); signup.setPhone(request.getPhone()); signup.setRemark(request.getRemark());
        signup.setStatus(SignupStatus.SIGNED_UP.getCode()); signup.setCreateTime(now); signup.setUpdateTime(now);
        if(signups.insert(signup)!=1) throw new BusinessException(ErrorCode.DB_ERROR);
        cache.evictAfterCommit(activity.getId());
        events.publishEvent(new ActivityParticipationChanged(activity.getId(),activity.getUserId(),actor,
                activity.getTitle(),request.getRealName(),
                activity.getCurrentPeople()+1==activity.getMaxPeople(),ActivityParticipationChanged.Kind.SIGNED_UP));
    }

    @Transactional
    public void cancel(Long id) {
        Long actor=actor();
        Activity activity=lock(id);
        // Cancellation remains available while awaiting review or after an offline action,
        // but never once the event has started.
        LocalDateTime now=LocalDateTime.now();
        if(Objects.equals(activity.getStatus(),1) || Objects.equals(activity.getStatus(),2)
                || activity.getStartTime()==null || !activity.getStartTime().isAfter(now))
            throw new BusinessException(ErrorCode.ACTIVITY_ENDED,"活动已开始，不能取消报名");
        ActivitySignup signup=signup(id,actor);
        if(signup==null || !Objects.equals(signup.getStatus(),SignupStatus.SIGNED_UP.getCode()))
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_SIGNUP);
        // Preserve the existing physical-delete contract: legacy readers and absence
        // statistics treat every remaining row as a participant.
        if(signups.deleteById(signup.getId())!=1 || activities.release(id)!=1)
            throw new BusinessException(ErrorCode.DB_ERROR,"报名人数与记录不一致");
        cache.evictAfterCommit(id);
    }

    @Transactional
    public void signIn(Long id,Long userId) {
        Long actor=actor();
        Activity activity=lock(id);
        if(!Objects.equals(activity.getUserId(),actor)) throw new BusinessException(ErrorCode.FORBIDDEN);
        LocalDateTime now=LocalDateTime.now();
        if(!Objects.equals(activity.getAuditStatus(),1) || Objects.equals(activity.getStatus(),3))
            throw new BusinessException(ErrorCode.PARAM_ERROR,"活动未通过审核或已下架");
        if(Objects.equals(activity.getStatus(),2) || activity.getEndTime()==null || !activity.getEndTime().isAfter(now))
            throw new BusinessException(ErrorCode.ACTIVITY_ENDED);
        if(userId==null || userId<=0) throw new BusinessException(ErrorCode.PARAM_ERROR);
        ActivitySignup signup=signup(id,userId);
        if(signup==null) throw new BusinessException(ErrorCode.ACTIVITY_NOT_SIGNUP);
        if(!Objects.equals(signup.getStatus(),SignupStatus.SIGNED_UP.getCode()) || signup.getCheckInTime()!=null)
            throw new BusinessException(ErrorCode.PARAM_ERROR,"当前报名状态不允许签到");
        ActivitySignup change=new ActivitySignup();
        change.setId(signup.getId()); change.setStatus(SignupStatus.CHECKED_IN.getCode());
        change.setCheckInTime(now); change.setUpdateTime(now);
        if(signups.updateById(change)!=1) throw new BusinessException(ErrorCode.DB_ERROR);
        cache.evictAfterCommit(id);
        events.publishEvent(new ActivityParticipationChanged(id,activity.getUserId(),userId,
                activity.getTitle(),signup.getRealName(),false,ActivityParticipationChanged.Kind.CHECKED_IN));
    }

    private Long actor() {
        Long id=UserContext.getUserId();
        if(id==null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return id;
    }
    private Activity lock(Long id) {
        if(id==null || id<=0) throw new BusinessException(ErrorCode.PARAM_ERROR);
        Activity activity=activities.lock(id);
        if(activity==null || !Objects.equals(activity.getDeleted(),0))
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        return activity;
    }
    private ActivitySignup signup(Long id,Long user) {
        // A locking read also observes the latest committed row in a REPEATABLE READ transaction.
        return signups.selectOne(new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getActivityId,id).eq(ActivitySignup::getUserId,user).last("FOR UPDATE"));
    }
    private void requireOpen(Activity activity,LocalDateTime now) {
        if(!Objects.equals(activity.getAuditStatus(),1) || !Objects.equals(activity.getStatus(),0)
                || activity.getStartTime()==null || !activity.getStartTime().isAfter(now))
            throw new BusinessException(ErrorCode.PARAM_ERROR,"活动不在可报名状态");
    }
}
