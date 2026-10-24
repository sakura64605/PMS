package com.hongjie.pms.refactor.comment.event;

import com.hongjie.pms.refactor.core.enums.BizTargetType;
import com.hongjie.pms.refactor.core.event.DomainEvent;
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
    @Override public String eventType() { return "CommentCreated"; }
    @Override public String aggregateType() { return "Comment"; }
    @Override public Long aggregateId() { return commentId; }
}
