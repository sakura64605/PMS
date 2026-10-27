package com.hongjie.pms.modules.community.comment.api.event;

import com.hongjie.pms.shared.core.enums.BizTargetType;
import com.hongjie.pms.shared.core.event.DomainEvent;
import lombok.*;

/** Self-comments also change counters, independently of notification recipients. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class CommentCreatedEvent extends DomainEvent {
    private Long commentId;
    private BizTargetType targetType;
    private Long targetId;

    @Override
    public String eventType() {
        return "CommentCreated";
    }

    @Override
    public String aggregateType() {
        return "Comment";
    }

    @Override
    public Long aggregateId() {
        return commentId;
    }
}
