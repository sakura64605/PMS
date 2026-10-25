package com.hongjie.pms.searchservice;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongjie.pms.contracts.search.SearchProtocol;
import com.hongjie.pms.searchengine.SearchEngine;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.context.annotation.*;
@Configuration(proxyBeanMethods=false)
public class SearchEngineConfiguration {
    @Bean(destroyMethod="close") ElasticsearchTransport searchTransport(SearchServiceProperties properties,ObjectMapper mapper){
        var client=RestClient.builder(HttpHost.create(properties.elasticsearchUrl()))
            .setRequestConfigCallback(config->config.setConnectTimeout(1000).setSocketTimeout(2500)).build();
        return new RestClientTransport(client,new JacksonJsonpMapper(mapper));
    }
    @Bean SearchEngine searchEngine(ElasticsearchTransport transport){return new SearchEngine(new ElasticsearchClient(transport),SearchProtocol.INDEX_ALIAS);}
}
