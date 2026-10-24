package com.hongjie.pms.refactor.daily.listener;

import com.hongjie.pms.modules.daily.service.DailyCacheInvalidator;
import com.hongjie.pms.refactor.audit.AuditChanged;
import com.hongjie.pms.refactor.comment.event.CommentCreatedEvent;
import com.hongjie.pms.refactor.core.enums.BizTargetType;
import com.hongjie.pms.refactor.like.LikeChanged;
import com.hongjie.pms.refactor.report.ReportTargetChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DailyCacheEvictListener {
    private final DailyCacheInvalidator cache;

    @TransactionalEventListener
    public void commented(CommentCreatedEvent event) {
        if (event.getTargetType() == BizTargetType.DAILY) cache.evictAll();
    }

    @TransactionalEventListener
    public void liked(LikeChanged event) {
        if ("daily".equals(event.targetType()) || "daily_post".equals(event.targetType())) cache.evictAll();
    }

    @TransactionalEventListener
    public void reported(ReportTargetChanged event) {
        if (event.type() == BizTargetType.DAILY) cache.evictAll();
    }

    @TransactionalEventListener
    public void audited(AuditChanged event) {
        if ("daily".equals(event.type())) cache.evictAll();
    }
}
