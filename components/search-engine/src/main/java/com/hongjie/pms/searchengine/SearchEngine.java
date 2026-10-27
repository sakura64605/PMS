package com.hongjie.pms.searchengine;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.hongjie.pms.contracts.search.*;
import java.io.IOException;
import java.util.*;

/** Shared query implementation: local and remote execution use exactly the same DSL. */
public final class SearchEngine {
    private final ElasticsearchClient client;
    private final String alias;

    public SearchEngine(ElasticsearchClient client, String alias) {
        this.client = Objects.requireNonNull(client);
        this.alias = Objects.requireNonNull(alias);
    }

    public SearchRequest request(SearchQuery input) {
        var builder =
                new SearchRequest.Builder()
                        .index(alias)
                        .from((input.pageNum() - 1) * input.pageSize())
                        .size(input.pageSize())
                        .trackTotalHits(t -> t.enabled(true))
                        .source(s -> s.filter(f -> f.includes("type", "businessId")));
        builder.query(
                q ->
                        q.bool(
                                b -> {
                                    b.must(
                                            m ->
                                                    m.multiMatch(
                                                            mm ->
                                                                    mm.query(input.keyword())
                                                                            .fields(
                                                                                    List.of(
                                                                                            "title^3",
                                                                                            "content",
                                                                                            "topics^2",
                                                                                            "location",
                                                                                            "petName^2",
                                                                                            "petType",
                                                                                            "address",
                                                                                            "userName"))));
                                    b.filter(
                                            f ->
                                                    f.bool(
                                                            v -> {
                                                                v.should(
                                                                        s ->
                                                                                s.term(
                                                                                        t ->
                                                                                                t.field(
                                                                                                                "auditStatus")
                                                                                                        .value(
                                                                                                                1)));
                                                                if (input.actor() != null)
                                                                    v.should(
                                                                            s ->
                                                                                    s.term(
                                                                                            t ->
                                                                                                    t.field(
                                                                                                                    "userId")
                                                                                                            .value(
                                                                                                                    input
                                                                                                                            .actor())));
                                                                return v.minimumShouldMatch("1");
                                                            }));
                                    if (!input.types().isEmpty())
                                        b.filter(
                                                f ->
                                                        f.bool(
                                                                types -> {
                                                                    for (String type :
                                                                            input.types())
                                                                        types.should(
                                                                                s ->
                                                                                        s.term(
                                                                                                t ->
                                                                                                        t.field(
                                                                                                                        "type")
                                                                                                                .value(
                                                                                                                        type)));
                                                                    return types.minimumShouldMatch(
                                                                            "1");
                                                                }));
                                    return b;
                                }));
        if ("time".equals(input.sortBy()))
            builder.sort(s -> s.field(f -> f.field("createTime").order(SortOrder.Desc)));
        else if ("hot".equals(input.sortBy())) {
            for (String field : List.of("likeCount", "commentCount", "viewCount", "createTime"))
                builder.sort(s -> s.field(f -> f.field(field).order(SortOrder.Desc)));
        } else
            builder.sort(s -> s.score(f -> f.order(SortOrder.Desc)))
                    .sort(s -> s.field(f -> f.field("createTime").order(SortOrder.Desc)));
        builder.sort(s -> s.field(f -> f.field("type").order(SortOrder.Asc)))
                .sort(s -> s.field(f -> f.field("businessId").order(SortOrder.Asc)));
        return builder.build();
    }

    public SearchResult search(SearchQuery input) throws IOException {
        if (input.keyword().isBlank()) return new SearchResult(0, List.of());
        var response = client.search(request(input), Document.class);
        var candidates =
                response.hits().hits().stream()
                        .map(hit -> hit.source())
                        .filter(Objects::nonNull)
                        .filter(
                                doc ->
                                        doc.type() != null
                                                && Set.of("pet", "activity", "daily")
                                                        .contains(doc.type())
                                                && doc.businessId() != null
                                                && doc.businessId() > 0)
                        .map(doc -> new SearchResult.Candidate(doc.type(), doc.businessId()))
                        .toList();
        return new SearchResult(
                response.hits().total() == null ? 0 : response.hits().total().value(), candidates);
    }

    public record Document(String type, Long businessId) {}
}
