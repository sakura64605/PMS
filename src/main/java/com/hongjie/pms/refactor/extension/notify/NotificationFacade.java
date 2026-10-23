package com.hongjie.pms.refactor.extension.notify;

import com.hongjie.pms.common.enums.MessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 通知发送的统一入口 —— 模板方法。
 *
 * <p>它把老实现里被复制了 20 次的那套骨架固定下来：
 * <pre>
 *   定位策略 -> 渲染内容 -> 投递
 * </pre>
 * 老代码是「20 个 sendXxxNotification 方法，每个都自己 new 一个 UserMessage、
 * 逐个 setter 七个字段、再调 sendAndPush」。任何一处漏 set 某个字段（比如忘了 businessId），
 * 编译器不会报错，只会在线上表现为「点通知跳不过去」。现在这个骨架只写一次，
 * 各通知类型只需实现「怎么把上下文渲染成文案」。
 *
 * <p><b>为什么用策略 + 注册表，而不是一张更大的 switch、或把文案全塞进数据库模板表</b>：
 * <ul>
 *   <li>纯 switch：20 个分支、参数形状各异，每加一种通知都要回来改这个核心方法，违反开闭；</li>
 *   <li>纯数据库模板：静态文案（点赞、关注）确实可以，但像活动统计报告那种
 *       「报名 N 人、签到 M 人…」需要业务计算，模板会退化成 SpEL，反而更难维护。</li>
 * </ul>
 * 所以按「是否需要业务计算」分流：静态文案走 {@code pms_message_template} 表，
 * 需要计算的走策略实现。不是「全部数据驱动」才叫优雅。
 */
@Service
@RequiredArgsConstructor
public class NotificationFacade {

    private final NotificationHandlerRegistry registry;

    private final NotificationSender sender;

    /**
     * 发送一条通知。
     *
     * <p>泛型在编译期无法与 {@link MessageType} 建立对应关系，因此
     * {@code registry.get(type)} 返回的策略与传入的上下文类型不匹配时，
     * 会在 {@link NotificationHandler#build} 处抛 {@code ClassCastException} ——
     * 这是编程错误（注册表配置与调用方不一致），不是运行时数据错误，快速失败是合适的。
     */
    public <C extends NotificationContext> void notify(MessageType type, C context) {
        NotificationHandler<C> handler = registry.handlerFor(type);
        sender.send(handler.build(context));
    }
}
