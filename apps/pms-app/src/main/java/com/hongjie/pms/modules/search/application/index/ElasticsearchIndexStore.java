package com.hongjie.pms.modules.search.application.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import com.hongjie.pms.modules.search.infrastructure.search.document.UnifiedDoc;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.*;

@Component
@RequiredArgsConstructor
public class ElasticsearchIndexStore implements SearchIndexStore {
    private final ElasticsearchClient client;

    @FunctionalInterface
    private interface IO<T> {
        T run() throws IOException;
    }

    private <T> T call(IO<T> operation) {
        try {
            return operation.run();
        } catch (IOException failure) {
            throw new IllegalStateException("Search index IO failed", failure);
        }
    }

    @Override
    public boolean exists(String index) {
        return call(() -> client.indices().exists(r -> r.index(index)).value());
    }

    @Override
    public List<String> targets() {
        if (!call(() -> client.indices().existsAlias(r -> r.name(ALIAS)).value())) return List.of();
        return List.copyOf(
                call(() -> client.indices().getAlias(r -> r.name(ALIAS)).result()).keySet());
    }

    CreateIndexRequest definition(String index) {
        var builder =
                new CreateIndexRequest.Builder()
                        .index(index)
                        .settings(s -> s.numberOfShards("1").numberOfReplicas("0"));
        builder.mappings(
                m -> {
                    for (String field :
                            List.of("id", "type", "images", "userAvatar", "videoUrl", "petAge"))
                        m.properties(field, p -> p.keyword(k -> k));
                    for (String field : List.of("businessId", "userId"))
                        m.properties(field, p -> p.long_(l -> l));
                    for (String field :
                            List.of(
                                    "likeCount",
                                    "commentCount",
                                    "viewCount",
                                    "status",
                                    "auditStatus",
                                    "petGender",
                                    "maxPeople",
                                    "currentPeople")) m.properties(field, p -> p.integer(i -> i));
                    for (String field :
                            List.of(
                                    "title",
                                    "content",
                                    "topics",
                                    "location",
                                    "userName",
                                    "petName",
                                    "petType",
                                    "address"))
                        m.properties(
                                field,
                                p ->
                                        p.text(
                                                t ->
                                                        t.analyzer("ik_max_word")
                                                                .searchAnalyzer("ik_smart")));
                    for (String field : List.of("createTime", "updateTime", "startTime", "endTime"))
                        m.properties(
                                field,
                                p ->
                                        p.date(
                                                d ->
                                                        d.format(
                                                                "strict_date_optional_time_nanos||yyyy-MM-dd HH:mm:ss||epoch_millis")));
                    return m;
                });
        return builder.build();
    }

    @Override
    public void create(String index) {
        if (!call(() -> client.indices().create(definition(index)).acknowledged()))
            throw new IllegalStateException("Search index creation was not acknowledged");
    }

    @Override
    public void switchTo(List<String> previous, String next) {
        var response =
                call(
                        () ->
                                client.indices()
                                        .updateAliases(
                                                r -> {
                                                    for (String old : previous)
                                                        r.actions(
                                                                a ->
                                                                        a.remove(
                                                                                v ->
                                                                                        v.index(old)
                                                                                                .alias(
                                                                                                        ALIAS)));
                                                    return r.actions(
                                                            a ->
                                                                    a.add(
                                                                            v ->
                                                                                    v.index(next)
                                                                                            .alias(
                                                                                                    ALIAS)
                                                                                            .isWriteIndex(
                                                                                                    true)));
                                                }));
        if (!response.acknowledged())
            throw new IllegalStateException(
                    "Search alias switch was not acknowledged; verify cluster state");
    }

    @Override
    public void write(String index, List<UnifiedDoc> documents) {
        if (documents.isEmpty()) return;
        var request = new BulkRequest.Builder().requireAlias(ALIAS.equals(index));
        for (var doc : documents)
            request.operations(op -> op.index(i -> i.index(index).id(doc.getId()).document(doc)));
        var response = call(() -> client.bulk(request.build()));
        if (response.errors()) throw new IllegalStateException("Search bulk contains failed items");
    }

    @Override
    public void delete(String id) {
        call(() -> client.delete(r -> r.index(ALIAS).id(id)));
    }

    @Override
    public void refresh(String index) {
        var response = call(() -> client.indices().refresh(r -> r.index(index)));
        if (response.shards().failed().intValue() > 0)
            throw new IllegalStateException("Search index refresh failed");
    }
}
