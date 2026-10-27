package com.hongjie.pms.shared.core.page;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("分页抽象 PageQuery / PageResult / PageBridge")
class PageTest {

    @Nested
    @DisplayName("PageQuery 入参归一化")
    class QueryNormalization {

        @Test
        @DisplayName("缺省值与非法值回落到默认")
        void fallsBackToDefaults() {
            assertThat(PageQuery.of(null, null).normalizedPageNum()).isEqualTo(1);
            assertThat(PageQuery.of(null, null).normalizedPageSize()).isEqualTo(10);
            assertThat(PageQuery.of(0L, 0L).normalizedPageNum()).isEqualTo(1);
            assertThat(PageQuery.of(-5L, -1L).normalizedPageNum()).isEqualTo(1);
            assertThat(PageQuery.of(-5L, -1L).normalizedPageSize()).isEqualTo(10);
        }

        @Test
        @DisplayName("页大小封顶，防止恶意大分页拖垮数据库")
        void capsPageSize() {
            assertThat(PageQuery.of(1L, 100_000L).normalizedPageSize())
                    .isEqualTo(PageQuery.MAX_PAGE_SIZE);
            assertThat(PageQuery.of(1L, 100L).normalizedPageSize()).isEqualTo(100);
        }

        @Test
        @DisplayName("转 MyBatis-Plus 分页对象时使用归一化后的值")
        void toPageUsesNormalizedValues() {
            Page<Object> page = PageQuery.of(null, 500L).toPage();
            assertThat(page.getCurrent()).isEqualTo(1);
            assertThat(page.getSize()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("PageResult 转换与兜底")
    class ResultMapping {

        @Test
        @DisplayName("map 保留分页元信息，只换记录类型")
        void mapKeepsMetadata() {
            PageResult<String> source = PageResult.of(List.of("a", "b"), 25L, 2L, 10L);

            PageResult<Integer> mapped = source.map(String::length);

            assertThat(mapped.getRecords()).containsExactly(1, 1);
            assertThat(mapped.getTotal()).isEqualTo(25L);
            assertThat(mapped.getPageNum()).isEqualTo(2L);
            assertThat(mapped.getPageSize()).isEqualTo(10L);
            assertThat(mapped.getPages()).isEqualTo(3L);
        }

        @Test
        @DisplayName("空页 records 为空列表而非 null")
        void emptyIsNeverNull() {
            PageResult<Object> empty = PageResult.empty(1L, 10L);
            assertThat(empty.getRecords()).isEmpty();
            assertThat(empty.getTotal()).isZero();
            assertThat(empty.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("records 为 null 时按空处理，不抛 NPE")
        void nullRecordsTreatedAsEmpty() {
            PageResult<String> result = PageResult.of(null, 0L, 1L, 10L);
            assertThat(result.getRecords()).isEmpty();
            assertThat(result.map(String::length).getRecords()).isEmpty();
        }

        @Test
        @DisplayName("pages 按总数向上取整")
        void pagesRoundsUp() {
            assertThat(PageResult.of(List.of(), 21L, 1L, 10L).getPages()).isEqualTo(3L);
            assertThat(PageResult.of(List.of(), 20L, 1L, 10L).getPages()).isEqualTo(2L);
            assertThat(PageResult.of(List.of(), 0L, 1L, 10L).getPages()).isZero();
        }
    }

    @Nested
    @DisplayName("PageBridge 与老接口结构互转")
    class BridgeCompatibility {

        @Test
        @DisplayName("IPage -> PageResult -> IPage 往返后字段一致")
        void roundTripPreservesFields() {
            Page<String> origin = new Page<>(2L, 10L, 25L);
            origin.setRecords(List.of("x", "y"));

            IPage<String> back = PageBridge.toIPage(PageBridge.of(origin));

            assertThat(back.getCurrent()).isEqualTo(2L);
            assertThat(back.getSize()).isEqualTo(10L);
            assertThat(back.getTotal()).isEqualTo(25L);
            assertThat(back.getPages()).isEqualTo(3L);
            assertThat(back.getRecords()).containsExactly("x", "y");
        }

        @Test
        @DisplayName("convert 就地转换记录，返回仍是 IPage —— 老接口内部改造的入口")
        void convertKeepsIPageShape() {
            Page<String> origin = new Page<>(1L, 10L, 2L);
            origin.setRecords(List.of("aa", "bbb"));

            IPage<Integer> converted = PageBridge.convert(origin, String::length);

            assertThat(converted).isInstanceOf(IPage.class);
            assertThat(converted.getRecords()).containsExactly(2, 3);
            assertThat(converted.getTotal()).isEqualTo(2L);
        }
    }
}
