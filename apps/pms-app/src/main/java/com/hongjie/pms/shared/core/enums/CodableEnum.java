package com.hongjie.pms.shared.core.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

import java.io.Serializable;
import java.util.Objects;

/**
 * 统一枚举契约。
 *
 * <p><b>共享的是契约，不是取值。</b> 每个限界上下文定义自己的枚举，因为同一数字在不同表语义相反：
 * {@code user.status} 0=禁用，而 {@code notice.status} 0=草稿，{@code comment.status} 0=已删除。
 * 强行统一取值域会让不同领域的码值互相污染，且需要重写老数据（老表禁改）。
 *
 * <p>实现本接口的枚举可直接作为实体字段类型，MyBatis-Plus 的 {@code MybatisEnumTypeHandler}
 * 会依据 {@link #getValue()} 做枚举与数据库值的双向转换，从而消灭 {@code setStatus(1)} 这类魔法值。
 * 老实体的状态字段仍为 {@code Integer}，不受影响。
 *
 * @param <C> 业务码类型（落库的值）。上界 {@code Serializable} 来自 MyBatis-Plus 的
 *            {@code IEnum<T extends Serializable>}，不加上界会编译失败
 */
public interface CodableEnum<C extends Serializable> extends IEnum<C> {

    /** 业务码，即落库的值 */
    C getCode();

    /** 展示文案，替代手写的 {@code if (status == 0) return "待处理"} */
    String getDesc();

    /** MyBatis-Plus 依据此值完成枚举与数据库值的双向转换 */
    @Override
    default C getValue() {
        return getCode();
    }

    /**
     * 按业务码解析枚举。
     *
     * @return 匹配的枚举；未匹配返回 {@code null}（调用方可用 {@code Optional} 包装或走 {@link #descOf}）
     */
    static <C extends Serializable, E extends Enum<E> & CodableEnum<C>> E of(
            Class<E> type, C code) {
        for (E candidate : type.getEnumConstants()) {
            if (Objects.equals(candidate.getCode(), code)) {
                return candidate;
            }
        }
        return null;
    }

    /** 按业务码取文案，未匹配时返回兜底文案（避免调用方到处判空） */
    static <C extends Serializable, E extends Enum<E> & CodableEnum<C>> String descOf(
            Class<E> type, C code, String fallback) {
        E matched = of(type, code);
        return matched == null ? fallback : matched.getDesc();
    }
}
