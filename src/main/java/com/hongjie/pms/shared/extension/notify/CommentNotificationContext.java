package com.hongjie.pms.shared.extension.notify;

import com.hongjie.pms.shared.core.enums.BizTargetType;

/**
 * 评论通知的上下文。
 *
 * <p>同时覆盖两种场景：
 * <ul>
 *   <li>评论了你的内容 —— {@code targetType} 为帖子/活动/日常，
 *       {@code commentContent} 是用户输入的原话，文案由处理器补上「评论了你的帖子《…》」前缀；</li>
 *   <li>回复了你的评论 —— {@code targetType} 为 {@code PET_COMMENT}，
 *       {@code commentContent} 已由调用方拼好「回复了你的评论：…」，处理器直接使用。</li>
 * </ul>
 * 这与老实现的分支结构一致：老代码用 {@code default} 兜住回复场景。
 *
 * @param targetTitle    被评论内容的标题；回复评论场景下为 null
 * @param commentContent 评论正文（回复场景下已带前缀）
 */
public record CommentNotificationContext(
        Long receiverId,
        Long senderId,
        BizTargetType targetType,
        String targetTitle,
        String commentContent,
        Long businessId,
        String link) implements NotificationContext {
}
