package com.hongjie.pms.shared.extension.target;

/**
 * 可被评论的目标。
 *
 * <p>替代老代码 {@code CommentServiceImpl.createComment} 里对 {@code targetType} 的三段
 * {@code if / else if} 硬编码分支 —— 每个分支做的事完全相同：自增评论数、查作者、发通知。
 */
public interface Commentable extends TargetHandler {

    /** 评论数 +1 */
    void incrementCommentCount(Long targetId);
}
