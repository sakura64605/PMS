package com.hongjie.pms.modules.community.comment.api.event;

import com.hongjie.pms.shared.core.enums.BizTargetType;
import com.hongjie.pms.shared.core.event.DomainEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 「某条评论触发了发给某个人的通知」这一事实。
 *
 * <p><b>为什么一次评论可能发两个事件</b>：评论既可以通知内容作者，回复时还可以同时
 * 通知被回复的人。与其让一个事件携带「收件人列表」让监听器去遍历，
 * 不如让每个事件只表达「一条待投递的通知」—— 事件更小、语义更单一，
 * 发件箱里每一行也能独立地重试与标记成败（若打包成一个事件，
 * 其中一个收件人失败就得整批重投，另一个会被重复通知）。
 *
 * <p>{@code aggregateId} 指向评论本身，因此在发件箱里可以按评论追溯它触发的全部通知。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class CommentNotificationEvent extends DomainEvent {

    private Long commentId;

    /** 评论作者，即通知的触发者 */
    private Long authorId;

    private BizTargetType targetType;

    /** 被评论对象的 id（帖子/活动/日常的 id） */
    private Long targetId;

    /**
     * 落库到 {@code user_message.business_id} 的值。
     *
     * <p>刻意与 {@link #targetId} 分开：老实现在<b>两类通知里传的东西不一样</b> ——
     * 评论内容作者时传帖子 id，回复某条评论时传的是<b>被回复评论的 id</b>。
     * 后者语义上可疑（链接明明指向帖子，business_id 却是评论），
     * 但它会写进用户可见的消息记录，改动它属于产品决策而非重构范畴，
     * 所以这里显式保留两个字段、原样对齐老实现。
     */
    private Long businessId;

    /** 收件人 */
    private Long receiverId;

    /** 被评论内容的标题；回复评论场景下为 null */
    private String targetTitle;

    /** 评论正文；回复场景下已带「回复了你的评论：」前缀 */
    private String content;

    private String link;

    @Override
    public String eventType() {
        return "CommentNotification";
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
