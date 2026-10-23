package com.hongjie.pms.refactor.core.converter;

import com.hongjie.pms.refactor.core.page.PageResult;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 实体到视图的显式转换契约。
 *
 * <p><b>为什么自研而不引 MapStruct</b>（面试取舍点）：
 * <ol>
 *   <li>本项目硬约束是「老接口返回字段不能变」，而自动映射会把实体<b>新增字段</b>
 *       悄悄映射进 DTO，等于静默改变 API 响应。手写转换器是显式白名单，加字段必须改代码。</li>
 *   <li>老 DTO 装配不是纯 1:1（如 {@code PetListResponseDto} 需聚合作者信息、收藏态、
 *       审核态），MapStruct 这部分仍要写 {@code @AfterMapping}，收益被吃掉。</li>
 *   <li>转换器是纯函数，单测一行一个断言；将来某模块确需 MapStruct 时，
 *       本接口不变、实现类可替换。</li>
 * </ol>
 *
 * @param <S> 源类型（实体）
 * @param <T> 目标类型（视图 / DTO）
 */
public interface EntityConverter<S, T> {

    T toView(S source);

    /** 列表转换，入参为 null 时返回空列表而非 null，避免调用方到处判空 */
    default List<T> toViews(List<S> sources) {
        if (sources == null || sources.isEmpty()) {
            return Collections.emptyList();
        }
        return sources.stream().map(this::toView).toList();
    }

    /** 分页转换，保留分页元信息 —— 替代手写「构造 Page → 遍历 → setRecords + setTotal 回填」 */
    default PageResult<T> toPage(PageResult<S> source) {
        return source.map(this::toView);
    }

    /** 允许为 null 的转换（如可选的关联对象） */
    default T toViewOrNull(S source) {
        return Objects.isNull(source) ? null : toView(source);
    }
}
