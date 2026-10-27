// modules/search/config/ElasticsearchConfig.java
package com.hongjie.pms.modules.search.infrastructure.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    @Value("${elasticsearch.host:192.168.232.128}")
    private String host;

    @Value("${elasticsearch.port:9200}")
    private Integer port;

    @Bean(destroyMethod = "close")
    public ElasticsearchTransport pmsElasticsearchTransport() {
        RestClient restClient = RestClient.builder(new HttpHost(host, port, "http")).build();

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule()); // 注册 JSR310 模块
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // 使用 ISO 格式

        JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper(mapper);
        return new RestClientTransport(restClient, jsonpMapper);
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }
}
