package com.hongjie.pms.modules.search.application.cloud;

import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "pms.search.mode", havingValue = "cloud")
@EnableFeignClients(clients = SearchFeignClient.class)
public class CloudSearchConfiguration {
    // Feign resolves this collaborator from the parent application context.
    @Bean
    org.springframework.cloud.openfeign.CircuitBreakerNameResolver searchBreakerName() {
        return (name, target, method) ->
                method.getDeclaringClass() == SearchFeignClient.class
                        ? "searchCandidates"
                        : (target.type().getSimpleName() + method.getName())
                                .replaceAll("[^a-zA-Z0-9]", "");
    }
}
