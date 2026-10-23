package com.hongjie.pms.refactor.extension.target;

import com.hongjie.pms.refactor.core.enums.BizTargetType;

import java.util.Set;

/**
 * 多态互动目标的基础处理器 —— 各类互动（评论 / 点赞 / 收藏 / 举报）共同需要的最小能力集。
 *
 * <p>刻意保持<b>窄</b>：只声明「任何目标都必然能回答」的三件事。各互动方式特有的能力
 * 放在 {@link Commentable} / {@link Likeable} / {@link Favorable} / {@link Reportable}
 * 等能力接口里，由目标按自身支持情况选择性实现。
 *
 * <p><b>为什么不合成一个万能接口</b>：审计目标有 {@code updateAuditStatus}，评论目标没有；
 * 硬合并会迫使每个实现类写一堆抛 {@code UnsupportedOperationException} 的空方法，
 * 违反接口隔离原则。共享的是 {@code StrategyRegistry} 这套<b>注册机制</b>，不是接口本身。
 *
 * @see TargetHandlerRegistry
 */
public interface TargetHandler {

    /** 本处理器负责的目标类型（一个处理器可服务多种类型） */
    Set<BizTargetType> supportTypes();

    /** 目标作者 ID —— 发通知时的接收者 */
    Long ownerId(Long targetId);

    /** 目标标题 —— 用于通知文案与审核记录 */
    String title(Long targetId);

    /** 目标详情页深链 —— 用于通知跳转 */
    String detailLink(Long targetId);
}
