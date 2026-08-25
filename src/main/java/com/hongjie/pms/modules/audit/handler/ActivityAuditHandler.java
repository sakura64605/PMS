package com.hongjie.pms.modules.audit.handler;

import com.hongjie.pms.common.cache.DistributedCache;
import com.hongjie.pms.common.cache.toolkit.CacheUtil;
import com.hongjie.pms.common.enums.AuditStatus;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.enums.TargetType;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.activity.entity.Activity;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.message.service.MessageService;
import com.hongjie.pms.modules.search.event.ActivityUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ActivityAuditHandler implements AuditTargetHandler {

    private final ActivityMapper activityMapper;
    private final MessageService messageService;
    private final DistributedCache distributedCache;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public List<String> getTargetTypes() {
        return List.of(TargetType.ACTIVITY.getCode());
    }

    @Override
    public Object getDetail(Long id) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "活动不存在");
        }
        return activity;
    }

    @Override
    public void updateAuditStatus(Long targetId, Integer auditStatus, String rejectReason) {
        Activity activity = activityMapper.selectById(targetId);
        if (activity != null) {
            activity.setAuditStatus(auditStatus);
            if (AuditStatus.APPROVED.getCode().equals(auditStatus)) {
                activity.setStatus(0);
                messageService.sendAuditPassNotification(
                        activity.getUserId(), activity.getTitle(), activity.getId(), "activity");
            } else if (AuditStatus.REJECTED.getCode().equals(auditStatus)) {
                activity.setStatus(3);
                messageService.sendAuditRejectNotification(
                        activity.getUserId(), activity.getTitle(), activity.getId(), "activity", rejectReason);
            }
            activityMapper.updateById(activity);
            eventPublisher.publishEvent(new ActivityUpdatedEvent(this, targetId, "audit"));
        }
    }

    @Override
    public void clearCache(Long targetId) {
        String activityCacheKey = CacheUtil.buildKey("activity", String.valueOf(targetId));
        distributedCache.delete(activityCacheKey);
        String activityListCacheKey = CacheUtil.buildKey("activityList", "1", "10");
        distributedCache.delete(activityListCacheKey);
    }

    @Override
    public String getTargetTypeDesc(String targetType) {
        return "活动";
    }

    @Override
    public String getTitle(Long targetId) {
        Activity activity = activityMapper.selectById(targetId);
        return activity != null ? activity.getTitle() : null;
    }

    @Override
    public Long getUserId(Long targetId) {
        Activity activity = activityMapper.selectById(targetId);
        return activity != null ? activity.getUserId() : null;
    }
}