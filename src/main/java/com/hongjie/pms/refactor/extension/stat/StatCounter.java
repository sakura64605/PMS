package com.hongjie.pms.refactor.extension.stat;

import java.time.LocalDate;

/**
 * 单项统计指标计数器。
 *
 * <p>替代 {@code StatisticsService} 中 12 个近乎相同的 {@code countNewXxx} 方法
 * （行 618–672）—— 它们全是 {@code mapper.selectCount(wrapper)} 的变体，
 * 每多一个指标就要多抄一遍方法。
 *
 * <p>同时统一了 {@code fillXxxStatistics}（行 482–559）与
 * {@code regenerateXxxByDate}（行 562–613）两套重复计数逻辑：
 * 二者的唯一差异只是「统计哪一天」，用 {@link #count(LocalDate)} 的参数即可表达。
 */
public interface StatCounter {

    /** 指标名，与 {@code daily_statistics} 的字段语义对应 */
    String metric();

    /** 统计指定日期的新增量 */
    long count(LocalDate date);
}
