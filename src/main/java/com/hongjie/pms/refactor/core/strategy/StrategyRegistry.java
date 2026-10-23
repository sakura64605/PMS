package com.hongjie.pms.refactor.core.strategy;

import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * 通用策略注册表 —— 把项目中三套各自实现的注册机制收敛成一套。
 *
 * <p>被泛化的三套现有实现：
 * <ul>
 *   <li>{@code modules.audit.handler.AuditTargetHandlerFactory}（手写 HashMap + 双重循环）</li>
 *   <li>{@code common.idempotent.handler.IdempotentExecuteHandlerFactory}</li>
 *   <li>{@code AI.tool.ToolRegistry}</li>
 * </ul>
 *
 * <p><b>设计要点</b>：
 * <ul>
 *   <li>构造期即建表并检测重复键，冲突抛 {@link IllegalStateException} —— 启动即失败，
 *       而不是等到线上某个分支才暴露「两个策略抢同一个 key」。</li>
 *   <li>key 提取用 {@link Function} 而非接口方法，使策略接口本身保持窄（ISP）：
 *       不需要为了被注册而在接口上挂 {@code getKey()}。</li>
 *   <li>共享的是<b>注册机制</b>，不是策略接口。审计有 {@code updateAuditStatus} 而评论没有，
 *       硬合成一个万能接口会违反接口隔离原则。</li>
 * </ul>
 *
 * @param <K> 策略键
 * @param <S> 策略类型
 */
public class StrategyRegistry<K, S> {

    private final String name;
    private final Map<K, S> strategies;

    /**
     * @param name         注册表名称，用于错误信息
     * @param strategies   Spring 按类型注入的全部策略实现
     * @param keyExtractor 从一个策略上取出它负责的全部键（一个策略可服务多个键）
     */
    public StrategyRegistry(String name, Collection<S> strategies, Function<S, Collection<K>> keyExtractor) {
        this.name = name;
        Map<K, S> table = new LinkedHashMap<>();
        for (S strategy : strategies) {
            for (K key : keyExtractor.apply(strategy)) {
                S previous = table.put(key, strategy);
                if (previous != null) {
                    throw new IllegalStateException(String.format(
                            "%s 策略键重复: %s 同时被 %s 与 %s 注册",
                            name, key, previous.getClass().getSimpleName(), strategy.getClass().getSimpleName()));
                }
            }
        }
        this.strategies = Collections.unmodifiableMap(table);
    }

    /** 按键取策略，不存在即抛业务异常 */
    public S get(K key) {
        return find(key).orElseThrow(() -> new BusinessException(
                ErrorCode.PARAM_ERROR, "未注册的 " + name + " 类型: " + key));
    }

    public Optional<S> find(K key) {
        return key == null ? Optional.empty() : Optional.ofNullable(strategies.get(key));
    }

    /**
     * 按键取策略并校验它实现了某项能力接口。
     *
     * <p>用于「同一目标类型可能只支持点赞不支持收藏」这类场景：
     * {@code registry.get(target, Likeable.class)} 在目标不支持该能力时立刻失败，
     * 而不是把 {@code ClassCastException} 留到调用点。
     */
    public <T extends S> T get(K key, Class<T> capability) {
        S strategy = get(key);
        if (!capability.isInstance(strategy)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, String.format(
                    "%s 的 %s 不支持 %s 能力", key, strategy.getClass().getSimpleName(), capability.getSimpleName()));
        }
        return capability.cast(strategy);
    }

    public Optional<S> find(K key, Class<? extends S> capability) {
        return find(key).filter(capability::isInstance);
    }

    /**
     * 全部策略（按注册顺序去重），用于批量场景如「逐类型统计」。
     *
     * <p>刻意用 {@code LinkedHashSet} 而非 {@code Set.copyOf} —— 后者不保证迭代顺序，
     * 会让「按固定顺序写日志/断言」的调用方拿到不稳定的结果。
     */
    public Collection<S> all() {
        return Collections.unmodifiableCollection(new LinkedHashSet<>(strategies.values()));
    }

    public Set<K> keys() {
        return strategies.keySet();
    }

    public boolean contains(K key) {
        return strategies.containsKey(key);
    }

    public int size() {
        return strategies.size();
    }

    public String getName() {
        return name;
    }
}
