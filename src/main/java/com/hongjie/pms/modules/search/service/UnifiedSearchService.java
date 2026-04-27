// modules/search/service/UnifiedSearchService.java
package com.hongjie.pms.modules.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.hongjie.pms.modules.search.document.UnifiedDoc;
import com.hongjie.pms.modules.search.dto.SearchResultItem;
import com.hongjie.pms.modules.search.dto.UnifiedSearchResponse;
import com.hongjie.pms.modules.user.dto.UserSimpleDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedSearchService {

    private final ElasticsearchClient esClient;
    private final SearchDataSyncService syncService;

    /**
     * 全局搜索
     */
    public UnifiedSearchResponse search(String keyword, Integer pageNum, Integer pageSize,
                                         List<String> types, String sortBy, Long currentUserId) {
        if (!StringUtils.hasText(keyword)) {
            return buildEmptyResponse(pageNum, pageSize);
        }

        try {
            // 构建搜索请求
            SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
                    .index("unified_search")
                    .from((pageNum - 1) * pageSize)
                    .size(pageSize);

            // 构建查询条件
            buildQuery(searchBuilder, keyword, types);

            // 构建高亮
            buildHighlight(searchBuilder);

            // 构建排序
            buildSort(searchBuilder, sortBy);

            // 执行搜索
            SearchResponse<UnifiedDoc> response = esClient.search(
                    searchBuilder.build(), UnifiedDoc.class);

            // 转换结果
            return convertToResponse(response, pageNum, pageSize, keyword, currentUserId);

        } catch (Exception e) {
            log.error("ES搜索失败: keyword={}", keyword, e);
            return buildEmptyResponse(pageNum, pageSize);
        }
    }

    /**
     * 构建查询条件
     */
    private void buildQuery(SearchRequest.Builder builder, String keyword, List<String> types) {
        builder.query(q -> q
                .bool(b -> {
                    // 主查询：多字段匹配
                    b.must(m -> m
                            .multiMatch(mm -> mm
                                    .query(keyword)
                                    .fields(List.of("title^3", "content", "topics^2", "location",
                                            "petName^2", "petType", "address", "userName"))
                                    .type(TextQueryType.BestFields)
                            )
                    );

                    // 过滤条件：只返回审核通过且未删除的
                    b.filter(f -> f.term(t -> t.field("auditStatus").value(1)));

                    // 类型过滤
                    if (types != null && !types.isEmpty()) {
                        // 构建一个 bool 查询，每个类型是一个 should 条件
                        co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder typeBoolBuilder =
                                new co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder();

                        for (String type : types) {
                            typeBoolBuilder.should(should -> should
                                    .term(term -> term
                                            .field("type")
                                            .value(type)
                                    )
                            );
                        }
                        typeBoolBuilder.minimumShouldMatch("1");

                        // 添加到 filter 中
                        b.filter(f -> f.bool(typeBoolBuilder.build()));
                    }

                    return b;
                }));
    }

    /**
     * 构建高亮
     */
    private void buildHighlight(SearchRequest.Builder builder) {
        builder.highlight(h -> h
                .fields("title", HighlightField.of(hf -> hf
                        .numberOfFragments(0)
                        .preTags("<em class='highlight'>")
                        .postTags("</em>")
                ))
                .fields("content", HighlightField.of(hf -> hf
                        .numberOfFragments(2)
                        .fragmentSize(100)
                        .preTags("<em class='highlight'>")
                        .postTags("</em>")
                ))
        );
    }

    /**
     * 构建排序
     */
    private void buildSort(SearchRequest.Builder builder, String sortBy) {
        if ("time".equals(sortBy)) {
            builder.sort(s -> s.field(f -> f.field("createTime").order(SortOrder.Desc)));
        } else if ("hot".equals(sortBy)) {
            // 热度分 = likeCount*3 + commentCount*2 + viewCount*1
            builder.sort(s -> s.field(f -> f.field("likeCount").order(SortOrder.Desc)));
            builder.sort(s -> s.field(f -> f.field("commentCount").order(SortOrder.Desc)));
            builder.sort(s -> s.field(f -> f.field("viewCount").order(SortOrder.Desc)));
            builder.sort(s -> s.field(f -> f.field("createTime").order(SortOrder.Desc)));
        } else {
            // 默认：相关度排序
            builder.sort(s -> s.score(f -> f.order(SortOrder.Desc)));
            builder.sort(s -> s.field(f -> f.field("createTime").order(SortOrder.Desc)));
        }
    }

    /**
     * 转换响应结果
     */
    private UnifiedSearchResponse convertToResponse(SearchResponse<UnifiedDoc> response,
                                                     Integer pageNum, Integer pageSize,
                                                     String keyword, Long currentUserId) {
        List<SearchResultItem> items = new ArrayList<>();

        for (Hit<UnifiedDoc> hit : response.hits().hits()) {
            UnifiedDoc doc = hit.source();
            if (doc == null) continue;

            SearchResultItem item = SearchResultItem.builder()
                    .type(doc.getType())
                    .id(doc.getBusinessId())
                    .title(doc.getTitle())
                    .content(doc.getContent())
                    .images(doc.getImages() != null && !doc.getImages().isEmpty() ?
                            List.of(doc.getImages().get(0)) : null)
                    .location(doc.getLocation())
                    .user(UserSimpleDto.builder()
                            .userId(doc.getUserId())
                            .username(doc.getUserName())
                            .nickname(doc.getUserName())
                            .avatar(doc.getUserAvatar())
                            .build())
                    .likeCount(doc.getLikeCount())
                    .commentCount(doc.getCommentCount())
                    .viewCount(doc.getViewCount())
                    .createTime(doc.getCreateTime())
                    .build();

            // 设置高亮
            if (hit.highlight() != null) {
                if (hit.highlight().get("title") != null && !hit.highlight().get("title").isEmpty()) {
                    item.setHighlightTitle(hit.highlight().get("title").get(0));
                } else {
                    item.setHighlightTitle(doc.getTitle());
                }

                if (hit.highlight().get("content") != null && !hit.highlight().get("content").isEmpty()) {
                    item.setHighlightContent(String.join(" ... ", hit.highlight().get("content")));
                } else if (doc.getContent() != null && doc.getContent().length() > 150) {
                    item.setHighlightContent(doc.getContent().substring(0, 150) + "...");
                } else {
                    item.setHighlightContent(doc.getContent());
                }
            } else {
                item.setHighlightTitle(doc.getTitle());
                if (doc.getContent() != null && doc.getContent().length() > 150) {
                    item.setHighlightContent(doc.getContent().substring(0, 150) + "...");
                } else {
                    item.setHighlightContent(doc.getContent());
                }
            }

            // 根据类型设置特有字段
            if ("daily".equals(doc.getType())) {
                item.setTopics(doc.getTopics());
            } else if ("pet".equals(doc.getType())) {
                item.setPetName(doc.getPetName());
                item.setPetType(doc.getPetType());
                item.setPetAge(doc.getPetAge());
                item.setPetGender(doc.getPetGender());
                item.setAddress(doc.getAddress());
            } else if ("activity".equals(doc.getType())) {
                item.setMaxPeople(doc.getMaxPeople());
                item.setCurrentPeople(doc.getCurrentPeople());
                item.setStartTime(doc.getStartTime());
                item.setEndTime(doc.getEndTime());
            }

            items.add(item);
        }

        // 批量查询互动状态（点赞、收藏、报名）
        if (currentUserId != null && !items.isEmpty()) {
            enrichInteractStatus(items, currentUserId);
        }

        long total = response.hits().total() != null ? response.hits().total().value() : 0;
        int totalPages = (int) Math.ceil((double) total / pageSize);

        return UnifiedSearchResponse.builder()
                .total(total)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .totalPages(totalPages)
                .items(items)
                .build();
    }

    /**
     * 补充互动状态（点赞、收藏、报名）
     */
    private void enrichInteractStatus(List<SearchResultItem> items, Long userId) {
        // 分别处理不同类型
        List<Long> dailyIds = items.stream()
                .filter(i -> "daily".equals(i.getType()))
                .map(SearchResultItem::getId)
                .collect(Collectors.toList());
        List<Long> petIds = items.stream()
                .filter(i -> "pet".equals(i.getType()))
                .map(SearchResultItem::getId)
                .collect(Collectors.toList());
        List<Long> activityIds = items.stream()
                .filter(i -> "activity".equals(i.getType()))
                .map(SearchResultItem::getId)
                .collect(Collectors.toList());

        // TODO: 批量查询点赞记录、收藏记录、报名记录
        // 这里需要注入对应的Mapper进行查询
    }

    private UnifiedSearchResponse buildEmptyResponse(Integer pageNum, Integer pageSize) {
        return UnifiedSearchResponse.builder()
                .total(0L)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .totalPages(0)
                .items(new ArrayList<>())
                .build();
    }
}