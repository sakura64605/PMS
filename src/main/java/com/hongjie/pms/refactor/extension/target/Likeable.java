package com.hongjie.pms.refactor.extension.target;

/**
 * 可被点赞的目标。
 *
 * <p>替代 {@code LikeServiceImpl} 里对 {@code targetType} 的 switch 分支。
 */
public interface Likeable extends TargetHandler {

    /** 点赞数 +1 */
    void incrementLikeCount(Long targetId);

    /** 点赞数 -1 */
    void decrementLikeCount(Long targetId);
}
