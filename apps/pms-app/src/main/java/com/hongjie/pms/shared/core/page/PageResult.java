package com.hongjie.pms.shared.core.page;

import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 统一分页出参。
 *
 * <p>替代裸用 MyBatis-Plus {@code IPage} 的两类问题：
 * <ol>
 *   <li>各 Service 自行 {@code new Page<>(..., 0)} 兜底空页，散落 5 处以上；</li>
 *   <li>转换 records 时手写 {@code setRecords(...)} + {@code setTotal(...)} 回填。</li>
 * </ol>
 *
 * <p>与老接口的兼容：老 Controller 的返回类型仍是 {@code CommonResult<IPage<...>>}，
 * 内部可用本类但出口经 {@link PageBridge#toIPage} 转换，序列化字段与改造前逐字节一致。
 *
 * @param <T> 记录类型
 */
@Data
public class PageResult<T> {

    private List<T> records;

    private long total;

    private long pageNum;

    private long pageSize;

    private long pages;

    public static <T> PageResult<T> of(List<T> records, long total, long pageNum, long pageSize) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records == null ? Collections.emptyList() : records);
        result.setTotal(total);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setPages(pageSize <= 0 ? 0 : (total + pageSize - 1) / pageSize);
        return result;
    }

    /** 空页，统一兜底口径 */
    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return of(Collections.emptyList(), 0L, pageNum, pageSize);
    }

    public static <T> PageResult<T> empty(PageQuery query) {
        return empty(query.normalizedPageNum(), query.normalizedPageSize());
    }

    /**
     * 记录转换：把实体页映射为视图页，保留分页元信息。
     *
     * <p>替代「构造新 Page → 遍历 convert → setRecords + setTotal 回填」这一重复了数十次的样板。
     */
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        if (records == null) {
            return of(Collections.emptyList(), total, pageNum, pageSize);
        }
        // 刻意用显式循环而非 stream().map(...).toList()：
        // mapper 的通配符签名 (? super T / ? extends R) 会让 Stream 推断出捕获类型，
        // 结果类型是 List<capture#1 ? extends R>，无法赋给 List<R>。
        List<R> mapped = new ArrayList<>(records.size());
        for (T record : records) {
            mapped.add(mapper.apply(record));
        }
        return of(mapped, total, pageNum, pageSize);
    }

    public boolean isEmpty() {
        return records == null || records.isEmpty();
    }
}
