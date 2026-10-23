package com.hongjie.pms.refactor.comment.listener;

import com.hongjie.pms.common.enums.MessageType;
import com.hongjie.pms.refactor.extension.notify.CommentNotificationContext;
import com.hongjie.pms.refactor.extension.notify.NotificationFacade;
import com.hongjie.pms.refactor.comment.event.CommentNotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 评论通知的监听器 —— 通知在事务<b>提交之后</b>才发出。
 *
 * <p><b>这解决了什么</b>：老实现是在 {@code @Transactional} 的
 * {@code CommentServiceImpl.createComment} 里直接调
 * {@code messageService.sendCommentNotification}，而后者会立刻发 RocketMQ。
 * 一旦事务随后回滚（比如通知之后还有别写操作失败），消息却已经出去了 ——
 * 用户会收到一条指向不存在评论的通知，且无法撤回。
 *
 * <p>改用 {@link TransactionPhase#AFTER_COMMIT} 后，回滚意味着监听器根本不会执行。
 *
 * <p>用 {@code @Async} 让通知渲染与投递不占用请求线程；异常在此被捕获并记录，
 * 因为事务已经提交，通知失败不应该让一个已经成功的评论创建对外表现为失败。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommentNotificationListener {

    private final NotificationFacade notificationFacade;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentNotification(CommentNotificationEvent event) {
        try {
            notificationFacade.notify(MessageType.COMMENT, new CommentNotificationContext(
                    event.getReceiverId(),
                    event.getAuthorId(),
                    event.getTargetType(),
                    event.getTargetTitle(),
                    event.getContent(),
                    event.getBusinessId(),
                    event.getLink()));
        } catch (Exception e) {
            // 事务已提交，通知失败不能反向影响已成功的结果；留给兜底重投任务处理
            log.error("评论通知发送失败: commentId={}, receiverId={}",
                    event.getCommentId(), event.getReceiverId(), e);
        }
    }
}
