package com.hongjie.pms.modules.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchIndexInitializer {

    private final ElasticsearchClient esClient;
    private static final String INDEX_NAME = "unified_search";

    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void initIndex() {
        try {
            ExistsRequest existsRequest = ExistsRequest.of(e -> e.index(INDEX_NAME));
            boolean exists = esClient.indices().exists(existsRequest).value();

            if (!exists) {
                log.info("索引 {} 不存在，开始创建...", INDEX_NAME);
                createIndex();
            } else {
                log.info("索引 {} 已存在", INDEX_NAME);
            }
        } catch (Exception e) {
            log.error("检查/创建索引失败: {}", e.getMessage(), e);
        }
    }

    private void createIndex() throws Exception {
        // 读取mapping配置
        String mappingJson = readResourceFile("elasticsearch/mapping.json");
        
        CreateIndexRequest request = CreateIndexRequest.of(i -> i
                .index(INDEX_NAME)
                .settings(s -> s
                        .numberOfShards("3")
                        .numberOfReplicas("1")
                        .analysis(a -> a
                                .analyzer("ik_max_word", an -> an
                                        .custom(c -> c.tokenizer("ik_max_word"))
                                )
                                .analyzer("ik_smart", an -> an
                                        .custom(c -> c.tokenizer("ik_smart"))
                                )
                        )
                )
                .mappings(m -> m
                        .properties("id", p -> p.keyword(k -> k))
                        .properties("type", p -> p.keyword(k -> k))
                        .properties("businessId", p -> p.long_(l -> l))
                        .properties("title", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                        .properties("content", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                        .properties("topics", p -> p.text(t -> t.analyzer("ik_max_word")))
                        .properties("location", p -> p.text(t -> t.analyzer("ik_smart")))
                        .properties("images", p -> p.keyword(k -> k))
                        .properties("userId", p -> p.long_(l -> l))
                        .properties("userName", p -> p.text(t -> t.analyzer("ik_smart")))
                        .properties("userAvatar", p -> p.keyword(k -> k))
                        .properties("likeCount", p -> p.integer(i2 -> i2))
                        .properties("commentCount", p -> p.integer(i2 -> i2))
                        .properties("viewCount", p -> p.integer(i2 -> i2))
                        .properties("status", p -> p.integer(i2 -> i2))
                        .properties("auditStatus", p -> p.integer(i2 -> i2))
                        .properties("createTime", p -> p.date(d -> d.format("yyyy-MM-dd'T'HH:mm:ss")))
                        .properties("updateTime", p -> p.date(d -> d.format("yyyy-MM-dd'T'HH:mm:ss")))
                        .properties("videoUrl", p -> p.keyword(k -> k))
                        .properties("petName", p -> p.text(t -> t.analyzer("ik_smart")))
                        .properties("petType", p -> p.text(t -> t.analyzer("ik_smart")))
                        .properties("petAge", p -> p.keyword(k -> k))
                        .properties("petGender", p -> p.integer(i2 -> i2))
                        .properties("address", p -> p.text(t -> t.analyzer("ik_smart")))
                        .properties("maxPeople", p -> p.integer(i2 -> i2))
                        .properties("currentPeople", p -> p.integer(i2 -> i2))
                        .properties("startTime", p -> p.date(d -> d.format("yyyy-MM-dd'T'HH:mm:ss")))
                        .properties("endTime", p -> p.date(d -> d.format("yyyy-MM-dd'T'HH:mm:ss")))
                )
        );

        CreateIndexResponse response = esClient.indices().create(request);
        
        if (response.acknowledged()) {
            log.info("索引 {} 创建成功", INDEX_NAME);
        } else {
            log.warn("索引 {} 创建失败", INDEX_NAME);
        }
    }

    private String readResourceFile(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}