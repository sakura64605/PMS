package com.hongjie.pms.shared.core.page;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * {@link PageResult} 与 MyBatis-Plus {@code IPage} 的双向桥接。
 *
 * <p><b>为什么需要它</b>：老接口的响应体是 {@code IPage}，其序列化字段为
 * {@code records/total/size/current/pages/...}；而新抽象用的是 {@code pageNum/pageSize}。
 * 老接口冻结意味着这些字段名一个都不能变 —— {@link #toIPage} 负责在出口把新结构还原成
 * 老结构，使内部重构对前端完全透明。
 */
public final class PageBridge {

    private PageBridge() {
    }

    /** {@code IPage} -> {@link PageResult}（进入新抽象层时用） */
    public static <T> PageResult<T> of(IPage<T> page) {
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * {@link PageResult} -> {@code IPage}（新抽象层出口还原为老接口结构时用）。
     *
     * <p>字段名与 {@code Page} 完全一致，保证老接口 JSON 结构不变。
     */
    public static <T> IPage<T> toIPage(PageResult<T> result) {
        Page<T> page = new Page<>(result.getPageNum(), result.getPageSize(), result.getTotal());
        page.setRecords(result.getRecords());
        return page;
    }

    /**
     * 直接把一份 {@code IPage} 的记录转换后仍以 {@code IPage} 返回 —— 老接口内部做转换时的快捷方式。
     */
    public static <T, R> IPage<R> convert(IPage<T> source, java.util.function.Function<? super T, ? extends R> mapper) {
        return toIPage(of(source).map(mapper));
    }
}
