package com.hongjie.pms.modules.governance.audit.application.handler;

import com.hongjie.pms.platform.cache.DistributedCache;
import com.hongjie.pms.platform.cache.toolkit.CacheUtil;
import com.hongjie.pms.platform.enums.AuditStatus;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.enums.TargetType;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.content.daily.infrastructure.persistence.entity.DailyPost;
import com.hongjie.pms.modules.content.daily.infrastructure.persistence.mapper.DailyPostMapper;
import com.hongjie.pms.modules.search.api.event.DailyPostUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DailyPostAuditHandler implements AuditTargetHandler {

    private final DailyPostMapper dailyPostMapper;
    private final DistributedCache distributedCache;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public List<String> getTargetTypes() {
        return List.of(TargetType.DAILY.getCode());
    }

    @Override
    public Object getDetail(Long id) {
        DailyPost daily = dailyPostMapper.selectById(id);
        if (daily == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "日记不存在");
        }
        return daily;
    }

    @Override
    public void updateAuditStatus(Long targetId, Integer auditStatus, String rejectReason) {
        DailyPost daily = dailyPostMapper.selectById(targetId);
        if (daily != null) {
            daily.setAuditStatus(auditStatus);
            daily.setStatus(AuditStatus.APPROVED.getCode().equals(auditStatus) ? 1 : 0);
            dailyPostMapper.updateById(daily);
            eventPublisher.publishEvent(new DailyPostUpdatedEvent(this, targetId, "audit"));
        }
    }

    @Override
    public void clearCache(Long targetId) {
        String dailyCacheKey = CacheUtil.buildKey("daily", String.valueOf(targetId));
        distributedCache.delete(dailyCacheKey);
    }

    @Override
    public String getTargetTypeDesc(String targetType) {
        return "日常动态";
    }

    @Override
    public String getTitle(Long targetId) {
        DailyPost daily = dailyPostMapper.selectById(targetId);
        return daily != null ? daily.getContent() : null;
    }

    @Override
    public Long getUserId(Long targetId) {
        DailyPost daily = dailyPostMapper.selectById(targetId);
        return daily != null ? daily.getUserId() : null;
    }
}
