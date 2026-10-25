package com.hongjie.pms.shared.core.page;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

/**
 * 统一分页查询入参。
 *
 * <p>替代各模块自行 {@code new Page<>(pageNum, pageSize)} 的做法，并集中处理边界：
 * 页码从 1 开始，越界或非法值回落到默认值，避免 {@code pageSize=100000} 这类拖垮数据库的入参。
 */
@Data
public class PageQuery {

    public static final long DEFAULT_PAGE_NUM = 1L;
    public static final long DEFAULT_PAGE_SIZE = 10L;
    /** 单页上限，防止恶意大分页 */
    public static final long MAX_PAGE_SIZE = 100L;

    private Long pageNum;

    private Long pageSize;

    public static PageQuery of(Long pageNum, Long pageSize) {
        PageQuery query = new PageQuery();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        return query;
    }

    /** 归一化后的页码（恒 >= 1） */
    public long normalizedPageNum() {
        return pageNum == null || pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
    }

    /** 归一化后的页大小（1 <= size <= MAX_PAGE_SIZE） */
    public long normalizedPageSize() {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /** 转为 MyBatis-Plus 分页对象 */
    public <T> Page<T> toPage() {
        return new Page<>(normalizedPageNum(), normalizedPageSize());
    }
}
