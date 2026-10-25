package com.hongjie.pms.modules.activity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.activity.infrastructure.cache.ActivityCache;
import com.hongjie.pms.modules.activity.infrastructure.persistence.entity.Activity;
import com.hongjie.pms.modules.activity.infrastructure.persistence.mapper.ActivityMapper;
import com.hongjie.pms.modules.governance.audit.application.AuditCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ActivityLifecycleCommands {
    private final ActivityMapper activities;
    private final ActivityScheduleWriter schedules;
    private final AuditCommandService audits;
    private final ActivityCache cache;
    private final ApplicationEventPublisher events;

    @Transactional
    public void delete(Long id) { change(id, false); }
    @Transactional
    public void restore(Long id) { change(id, true); }

    private void change(Long id, boolean restore) {
        Long actor = UserContext.getUserId();
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        if (id == null || id <= 0) throw new BusinessException(ErrorCode.PARAM_ERROR);
        Activity row = activities.selectOne(new LambdaQueryWrapper<Activity>().eq(Activity::getId, id).last("FOR UPDATE"));
        if (row == null || !Objects.equals(row.getDeleted(), restore ? 1 : 0))
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        if (!Objects.equals(row.getUserId(), actor) && !(restore && UserContext.isAdmin()))
            throw new BusinessException(ErrorCode.FORBIDDEN);
        LocalDateTime now = LocalDateTime.now();
        if (Objects.equals(row.getStatus(), 2) || row.getEndTime() == null || !row.getEndTime().isAfter(now))
            throw new BusinessException(ErrorCode.ACTIVITY_ENDED);
        if (activities.update(null, new LambdaUpdateWrapper<Activity>().eq(Activity::getId, id)
                .eq(Activity::getDeleted, restore ? 1 : 0).ne(Activity::getStatus, 2)
                .set(Activity::getDeleted, restore ? 0 : 1).set(Activity::getUpdateTime, now)) != 1)
            throw new BusinessException(ErrorCode.DB_ERROR);
        if (restore) {
            if (Objects.equals(row.getAuditStatus(), 0)) audits.ensurePending("activity", id);
            schedules.replace(id, row.getStartTime(), row.getEndTime(), now);
        } else schedules.cancel(id);
        cache.evictAfterCommit(id);
        events.publishEvent(new ActivityContentChanged(id));
    }
}
