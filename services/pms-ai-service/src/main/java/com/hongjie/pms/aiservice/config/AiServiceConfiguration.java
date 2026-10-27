package com.hongjie.pms.aiservice.config;

import com.hongjie.pms.aiservice.core.AiCoreClient;
import com.hongjie.pms.modules.ai.api.websocket.AIAgentWebSocketHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.socket.config.annotation.*;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableWebSocket
public class AiServiceConfiguration {
    @Bean
    @LoadBalanced
    RestClient.Builder aiCoreHttpBuilder() {
        var factory =
                new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(factory);
    }

    @Bean
    AiCoreClient aiCoreClient(
            @LoadBalanced RestClient.Builder builder,
            @Value("${pms.ai.core.url:http://pms-core-service}") String url,
            @Value("${pms.ai.core.service-token:}") String secret) {
        return new AiCoreClient(builder.baseUrl(url).build(), secret);
    }

    @Bean
    MybatisPlusInterceptor aiMybatisInterceptor() {
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    WebSocketConfigurer aiWebsocket(AIAgentWebSocketHandler handler) {
        return registry ->
                registry.addHandler(handler, "/pet-system/ai/ws").setAllowedOriginPatterns("*");
    }
}
