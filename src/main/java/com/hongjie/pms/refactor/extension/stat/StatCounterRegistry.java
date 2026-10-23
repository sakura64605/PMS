package com.hongjie.pms.refactor.extension.stat;

import com.hongjie.pms.refactor.core.strategy.StrategyRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计指标注册表。
 *
 * <p><b>为什么用显式注册表而不是「反射按方法名调用」</b>：
 * 反射方案看起来更少的代码，但方法名变成字符串后 IDE 无法跳转、重命名即断、
 * 也无法被单元测试逐项枚举。显式注册表虽然每个指标要写 3 行，
 * 但它是可枚举、可测试、可静态分析的。
 */
@Component
public class StatCounterRegistry extends StrategyRegistry<String, StatCounter> {

    public StatCounterRegistry(ObjectProvider<StatCounter> counters) {
        super("统计指标计数器", counters.stream().toList(), counter -> List.of(counter.metric()));
    }

    /**
     * 一次性统计全部指标。
     *
     * <p>返回有序 Map（保持 Spring 注入顺序），便于按固定顺序写日志与断言。
     */
    public Map<String, Long> countAll(LocalDate date) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (StatCounter counter : all()) {
            result.put(counter.metric(), counter.count(date));
        }
        return result;
    }
}
