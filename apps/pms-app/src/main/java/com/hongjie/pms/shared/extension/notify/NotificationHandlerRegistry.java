package com.hongjie.pms.shared.extension.notify;

import com.hongjie.pms.shared.core.strategy.StrategyRegistry;
import com.hongjie.pms.platform.enums.MessageType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 消息类型 -> 通知策略 的注册表。
 *
 * <p>替代老 {@code MessageService} 中散布在 4 处的 {@code switch (targetType)} 分支
 * （行 97 / 115 / 156 / 167）。
 *
 * <p>用 {@link ObjectProvider} 注入以容忍「暂无处理器」的初始状态（集合型构造参数默认必填）。
 */
@Component
public class NotificationHandlerRegistry extends StrategyRegistry<MessageType, NotificationHandler<?>> {

    /**
     * Spring 装配用。显式标 {@code @Autowired}：本类有两个构造器，
     * 不标注的话 Spring 无法判断该用哪一个。
     */
    @Autowired
    public NotificationHandlerRegistry(ObjectProvider<NotificationHandler<?>> handlers) {
        this(handlers.stream().toList());
    }

    /**
     * 直接注入策略实例 —— 供测试使用。
     *
     * <p>包级可见：不为测试放宽为 public，也不为了构造一个 {@code ObjectProvider}
     * 而在测试里写一堆接口样板。这样「注册表按消息类型分发」这条链路可以被真正验证。
     */
    NotificationHandlerRegistry(List<NotificationHandler<?>> handlers) {
        super("通知处理器", handlers, handler -> List.of(handler.type()));
    }

    /**
     * 按消息类型取策略（带泛型收窄）。
     *
     * <p>泛型在编译期无法与 {@link MessageType} 建立对应关系（枚举无法携带类型参数），
     * 因此这里做一次受控的未检查转换 —— 调用方传入的上下文类型与处理器期望的类型不匹配时，
     * 会在 {@code build} 处抛 {@code ClassCastException}，属于编程错误而非运行时数据错误。
     *
     * <p><b>方法名刻意不叫 {@code get}</b>：父类 {@code StrategyRegistry&lt;MessageType, ?&gt;}
     * 已有一个 {@code get(MessageType)}，这里若同名同参，二者擦除后签名相同，
     * 既非重载也非覆盖，会直接编译报「对 get 的引用不明确」。
     */
    @SuppressWarnings("unchecked")
    public <C extends NotificationContext> NotificationHandler<C> handlerFor(MessageType type) {
        return (NotificationHandler<C>) super.get(type);
    }
}
