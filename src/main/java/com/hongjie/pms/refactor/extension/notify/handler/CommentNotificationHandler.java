package com.hongjie.pms.refactor.extension.notify.handler;

import com.hongjie.pms.refactor.core.enums.BizTargetType;
import com.hongjie.pms.common.enums.MessageType;
import com.hongjie.pms.refactor.extension.notify.CommentNotificationContext;
import com.hongjie.pms.refactor.extension.notify.NotificationHandler;
import com.hongjie.pms.refactor.extension.notify.NotificationMessage;
import org.springframework.stereotype.Component;

/**
 * 评论通知渲染。
 *
 * <p>文案与老实现 {@code MessageService.getCommentTitle/getCommentContent} 逐字一致，
 * 包括那个容易看漏的行为：<b>「回复了某条评论」会落到 default 分支</b>，
 * 得到「有人评论了你」+ 调用方已拼好的原文。这是有意的 —— 老代码里回复场景传的
 * {@code targetType} 是 {@code comment_reply}，在 switch 里没有对应 case。
 * 新实现保持同样的落点，避免改变用户实际看到的文案。
 */
@Component
public class CommentNotificationHandler implements NotificationHandler<CommentNotificationContext> {

    /** 与老实现一致：正文超过 100 字截断 */
    private static final int MAX_CONTENT_LENGTH = 100;

    @Override
    public MessageType type() {
        return MessageType.COMMENT;
    }

    @Override
    public NotificationMessage build(CommentNotificationContext ctx) {
        String title;
        String content;
        if (ctx.targetType() == BizTargetType.PET_POST) {
            title = "有人评论了你的帖子";
            content = "评论了你的帖子《" + ctx.targetTitle() + "》：" + ctx.commentContent();
        } else if (ctx.targetType() == BizTargetType.PET_ACTIVITY) {
            title = "有人评论了你的活动";
            content = "评论了你的活动《" + ctx.targetTitle() + "》：" + ctx.commentContent();
        } else {
            // PET_COMMENT（回复某条评论）与 DAILY 都落在这里，与老实现的 default 分支一致
            title = "有人评论了你";
            content = ctx.commentContent();
        }

        return new NotificationMessage(
                ctx.receiverId(),
                ctx.senderId(),
                type(),
                title,
                truncate(content),
                ctx.businessId(),
                ctx.link());
    }

    private String truncate(String content) {
        if (content == null || content.length() <= MAX_CONTENT_LENGTH) {
            return content;
        }
        return content.substring(0, MAX_CONTENT_LENGTH) + "...";
    }
}
