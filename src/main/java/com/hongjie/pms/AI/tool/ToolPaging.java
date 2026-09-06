package com.hongjie.pms.AI.tool;

/**
 * AI 工具分页的公共解析与结果引导。
 * 所有会返回多条数据的 BaseTool 都应支持分页，避免 LIMIT 截断后向用户误报"没有更多"。
 */
public final class ToolPaging {

    public static final int DEFAULT_PAGE_SIZE = 5;
    public static final int MAX_PAGE_SIZE = 20;

    private ToolPaging() {
    }

    /** 解析页码（≥1，默认1） */
    public static int page(Object value) {
        if (value instanceof Number n) {
            return Math.max(1, n.intValue());
        }
        return 1;
    }

    /** 解析每页条数（1~20，默认5） */
    public static int pageSize(Object value) {
        int size = value instanceof Number n ? n.intValue() : DEFAULT_PAGE_SIZE;
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * 在结果末尾追加翻页引导，让 LLM 知道还有更多、以及怎么取下一页。
     *
     * @param noun 计量词，如 只/个/条
     */
    public static void appendPagingFooter(StringBuilder sb, int page, int totalPages,
                                          long total, int pageSize, String noun) {
        if (page < totalPages) {
            long remaining = total - (long) page * pageSize;
            sb.append("还有 ").append(Math.max(0, remaining)).append(' ').append(noun)
                    .append("未展示。若用户想看更多，请再次调用本工具并传 page=").append(page + 1)
                    .append("（其余参数保持不变）。");
        } else {
            sb.append("已展示全部 ").append(total).append(' ').append(noun).append('。');
        }
    }
}
