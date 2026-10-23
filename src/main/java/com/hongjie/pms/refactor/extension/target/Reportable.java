package com.hongjie.pms.refactor.extension.target;

/**
 * 可被举报的目标。
 *
 * <p>替代 {@code ReportServiceImpl} 里三处各写一遍的
 * {@code switch (targetType)} 分支（行 130 / 203 / 222）。
 */
public interface Reportable extends TargetHandler {

    /** 举报列表/详情里展示的目标摘要（如帖子标题、评论内容前 N 字、用户名） */
    String summary(Long targetId);

    /** 举报成立时的处置动作，如内容下架。默认不处置，由需要处置的目标覆写。 */
    default void onReportAccepted(Long targetId) {
        // 默认空实现：并非所有目标都支持「下架」这类处置
    }
}
