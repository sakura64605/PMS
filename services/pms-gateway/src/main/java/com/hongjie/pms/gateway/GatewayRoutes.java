package com.hongjie.pms.gateway;

import org.springframework.cloud.gateway.route.*;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
public class GatewayRoutes {
    @Bean
    RouteLocator publicRoutes(RouteLocatorBuilder routes) {
        return routes.routes()
                .route(
                        "ai-websocket",
                        r ->
                                r.path("/pet-system/ai/ws")
                                        .and()
                                        .header("Upgrade", "(?i)websocket")
                                        .uri("lb:ws://pms-ai-service"))
                .route(
                        "ai-api",
                        r ->
                                r.path("/pet-system/ai/**", "/pet-system/admin/ai/**")
                                        .uri("lb://pms-ai-service"))
                .route(
                        "community-websocket",
                        r ->
                                r.path("/pet-system/ws", "/pet-system/ws/**")
                                        .and()
                                        .header("Upgrade", "(?i)websocket")
                                        .uri("lb:ws://pms-core-service"))
                .route("community-api", r -> r.path("/pet-system/**").uri("lb://pms-core-service"))
                .build();
    }
}
