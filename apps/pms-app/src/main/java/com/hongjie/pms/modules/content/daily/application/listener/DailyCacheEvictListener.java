package com.hongjie.pms.modules.content.daily.application.listener;

import com.hongjie.pms.modules.content.daily.api.service.DailyCacheInvalidator;
import com.hongjie.pms.modules.governance.audit.application.AuditChanged;
import com.hongjie.pms.modules.community.comment.api.event.CommentCreatedEvent;
import com.hongjie.pms.shared.core.enums.BizTargetType;
import com.hongjie.pms.modules.community.like.application.LikeChanged;
import com.hongjie.pms.modules.governance.report.application.ReportTargetChanged;
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
