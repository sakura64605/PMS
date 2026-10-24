package com.hongjie.pms.refactor.activity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.activity.cache.ActivityCache;
import com.hongjie.pms.modules.activity.dto.request.ActivityRequestDto;
import com.hongjie.pms.modules.activity.dto.response.ActivityPostRespDto;
import com.hongjie.pms.modules.activity.entity.Activity;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.feed.service.FeedService;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.refactor.audit.AuditCommandService;
import com.hongjie.pms.refactor.core.enums.biz.ActivityStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ActivityContentCommands {
    private final ActivityMapper activities;
    private final UserMapper users;
    private final AuditCommandService audits;
    private final ActivityScheduleWriter schedules;
    private final FeedService feed;
    private final ActivityCache cache;
    private final ApplicationEventPublisher events;

    @Transactional
    public ActivityPostRespDto publish(ActivityRequestDto request) {
        Long actor = actor();
        LocalDateTime now = LocalDateTime.now();
        validate(request, now);
        User user = users.selectById(actor);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        if (!Objects.equals(user.getStatus(), 1)) throw new BusinessException(ErrorCode.USER_DISABLED);
        Activity activity = content(request);
        activity.setUserId(actor);
        activity.setStatus(ActivityStatus.SIGNUP_OPEN.getCode());
        activity.setAuditStatus(0);
        activity.setDeleted(0);
        activity.setCurrentPeople(0);
        activity.setViewCount(0);
        activity.setLikeCount(0);
        activity.setCommentCount(0);
        activity.setShareCount(0);
        activity.setCreateTime(now);
        activity.setUpdateTime(now);
        if (activities.insert(activity) != 1) throw new BusinessException(ErrorCode.DB_ERROR);
        audits.submit("activity", activity.getId());
        schedules.replace(activity.getId(), activity.getStartTime(), activity.getEndTime(), now);
        // This projection is a database write and must roll back with the activity.
        feed.pushToFans(actor, activity.getId(), "activity", activity.getTitle(), activity.getImages(),
                user.getUserName(), user.getAvatar(), now);
        cache.evictAfterCommit(activity.getId());
        events.publishEvent(new ActivityContentChanged(activity.getId()));
        return ActivityPostRespDto.builder()
                .id(activity.getId()).userId(actor).title(activity.getTitle()).content(activity.getContent())
                .images(activity.getImages()).location(activity.getLocation()).maxPeople(activity.getMaxPeople())
                .status(activity.getStatus()).startTime(activity.getStartTime()).endTime(activity.getEndTime()).build();
    }

    @Transactional
    public void edit(ActivityRequestDto request) {
        Long actor = actor();
        if (request == null || request.getId() == null || request.getId() <= 0)
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        Activity current = activities.selectOne(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getId, request.getId()).last("FOR UPDATE"));
        if (current == null || !Objects.equals(current.getDeleted(), 0))
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        if (!Objects.equals(current.getUserId(), actor)
                || request.getUserId() != null && !Objects.equals(request.getUserId(), actor))
            throw new BusinessException(ErrorCode.FORBIDDEN);
        LocalDateTime now = LocalDateTime.now();
        if (Objects.equals(current.getStatus(), ActivityStatus.FINISHED.getCode())
                || current.getEndTime() == null || !current.getEndTime().isAfter(now))
            throw new BusinessException(ErrorCode.ACTIVITY_ENDED, "活动已结束，无法修改");
        // Preserve the existing future-start editing contract.
        validate(request, now);
        if (request.getMaxPeople() < Objects.requireNonNullElse(current.getCurrentPeople(), 0))
            throw new BusinessException(ErrorCode.PARAM_ERROR, "人数上限不能小于已报名人数");
        boolean changed = changed(current, request);
        if (!changed) {
            if (Objects.equals(current.getAuditStatus(), 0)) audits.ensurePending("activity", current.getId());
            return;
        }
        Activity change = content(request);
        change.setId(current.getId());
        change.setAuditStatus(0);
        change.setStatus(ActivityStatus.SIGNUP_OPEN.getCode());
        change.setUpdateTime(now);
        if (activities.update(change, new LambdaUpdateWrapper<Activity>().eq(Activity::getId, current.getId())
                .eq(Activity::getDeleted, 0).ne(Activity::getStatus, ActivityStatus.FINISHED.getCode())) != 1)
            throw new BusinessException(ErrorCode.DB_ERROR);
        // Reuse an open round; otherwise append one, preserving completed decisions.
        audits.ensurePending("activity", current.getId());
        if (!Objects.equals(current.getStartTime(), request.getStartTime())
                || !Objects.equals(current.getEndTime(), request.getEndTime()))
            schedules.replace(current.getId(), request.getStartTime(), request.getEndTime(), now);
        cache.evictAfterCommit(current.getId());
        events.publishEvent(new ActivityContentChanged(current.getId()));
    }

    private Activity content(ActivityRequestDto request) {
        Activity row = new Activity();
        row.setTitle(request.getTitle());
        row.setContent(request.getContent());
        row.setImages(images(request.getImages()));
        row.setLocation(request.getLocation());
        row.setMaxPeople(request.getMaxPeople());
        row.setStartTime(request.getStartTime());
        row.setEndTime(request.getEndTime());
        return row;
    }
    private boolean changed(Activity row, ActivityRequestDto request) {
        return !Objects.equals(row.getTitle(), request.getTitle())
                || !Objects.equals(row.getContent(), request.getContent())
                || !Objects.equals(images(row.getImages()), images(request.getImages()))
                || !Objects.equals(row.getLocation(), request.getLocation())
                || !Objects.equals(row.getMaxPeople(), request.getMaxPeople())
                || !Objects.equals(row.getStartTime(), request.getStartTime())
                || !Objects.equals(row.getEndTime(), request.getEndTime());
    }
    private List<String> images(List<String> values) { return values == null ? List.of() : List.copyOf(values); }
    private Long actor() {
        Long actor = UserContext.getUserId();
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return actor;
    }
    private void validate(ActivityRequestDto request, LocalDateTime now) {
        if (request == null || !StringUtils.hasText(request.getTitle()) || request.getTitle().length() > 100
                || !StringUtils.hasText(request.getContent()) || !StringUtils.hasText(request.getLocation())
                || request.getLocation().length() > 200 || request.getMaxPeople() == null || request.getMaxPeople() < 1
                || request.getStartTime() == null || request.getEndTime() == null
                || !request.getStartTime().isAfter(now) || !request.getEndTime().isAfter(request.getStartTime())
                || request.getImages() != null && request.getImages().stream().anyMatch(value -> !StringUtils.hasText(value)))
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请检查活动内容、人数与起止时间");
    }
}
