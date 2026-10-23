package com.hongjie.pms.refactor.extension.notify.handler;

import com.hongjie.pms.refactor.core.enums.BizTargetType;
import com.hongjie.pms.common.enums.MessageType;
import com.hongjie.pms.refactor.extension.notify.LikeNotificationContext;
import com.hongjie.pms.refactor.extension.notify.NotificationHandler;
import com.hongjie.pms.refactor.extension.notify.NotificationMessage;
import org.springframework.stereotype.Component;

/**
 * 点赞通知渲染。
 *
 * <p>替代 {@code MessageService.sendLikeNotification} 及其
 * {@code getLikeTitle} / {@code getLikeContent} 两个 switch —— 那两个 switch
 * 除了各自的 {@code default}，还有一个 {@code case "系统通知"} 死分支。
 * 改用枚举作为目标类型后，该分支在类型上已不可表达，自然消失。
 */
@Component
public class LikeNotificationHandler implements NotificationHandler<LikeNotificationContext> {

    @Override
    public MessageType type() {
        return MessageType.LIKE;
    }

    @Override
    public NotificationMessage build(LikeNotificationContext ctx) {
        String title = switch (ctx.targetType()) {
            case PET_POST -> "有人点赞了你的帖子";
            case PET_COMMENT -> "有人点赞了你的评论";
            case PET_ACTIVITY -> "有人点赞了你的活动";
            default -> "有人点赞了";
        };

        String content = switch (ctx.targetType()) {
            case PET_POST -> "点赞了你的帖子《" + ctx.targetTitle() + "》";
            case PET_COMMENT -> "点赞了你的评论";
            case PET_ACTIVITY -> "点赞了你的活动《" + ctx.targetTitle() + "》";
            default -> "点赞了《" + ctx.targetTitle() + "》";
        };

        return new NotificationMessage(
                ctx.receiverId(),
                ctx.senderId(),
                type(),
                title,
                content,
                ctx.businessId(),
                ctx.link());
    }
}
