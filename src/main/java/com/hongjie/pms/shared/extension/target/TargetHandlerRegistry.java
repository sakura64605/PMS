package com.hongjie.pms.shared.extension.target;

import com.hongjie.pms.shared.core.enums.BizTargetType;
import com.hongjie.pms.shared.core.strategy.StrategyRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 目标类型 -> 处理器 的注册表。
 *
 * <p>构造时由 Spring 注入全部 {@link TargetHandler} 实现并建立索引，
 * 调用方拿到后按类型直取，无需任何 {@code if / switch}。
 *
 * <p>新增一种可互动目标（比如「商品」）只需新增一个实现类并标注 {@code @Component}，
 * 本类与所有调用点<b>一行都不用改</b> —— 这就是开闭原则在此处的具体含义。
 *
 * <p>注入用 {@link ObjectProvider} 而非 {@code List}：Spring 对集合型构造参数默认
 * {@code required = true}，一个实现类都没有时会直接启动失败。注册表在「尚无任何策略」
 * 的初始状态下也应当能正常装配，只是查任何键都返回未注册而已。
 */
@Component
public class TargetHandlerRegistry extends StrategyRegistry<BizTargetType, TargetHandler> {

    /**
     * Spring 装配用。显式标 {@code @Autowired}：本类有两个构造器，
     * 不标注的话 Spring 无法判断该用哪一个。
     */
    @Autowired
    public TargetHandlerRegistry(ObjectProvider<TargetHandler> handlers) {
        this(handlers.stream().toList());
    }

    /**
     * 直接注入策略实例 —— 供测试使用。
     *
     * <p>公开而非包级可见：等价性测试位于另一个包，需要绕开 Spring 上下文直接构造本类，
     * 从而在不需要 Redis / RocketMQ / 完整应用启动的前提下验证服务行为。
     * 与之相对，{@code NotificationHandlerRegistry} 的同类构造器是包级可见的 ——
     * 因为它的测试恰好在同一个包。这里不一致是可见性要求使然，不是疏漏。
     */
    public TargetHandlerRegistry(List<TargetHandler> handlers) {
        super("目标类型处理器", handlers, TargetHandler::supportTypes);
    }
}
