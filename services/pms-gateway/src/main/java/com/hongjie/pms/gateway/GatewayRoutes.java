package com.hongjie.pms.gateway;
import org.springframework.cloud.gateway.route.*;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.*;
@Configuration(proxyBeanMethods=false)
public class GatewayRoutes {
    @Bean RouteLocator publicRoutes(RouteLocatorBuilder routes){
        return routes.routes()
            .route("community-websocket",r->r.path("/pet-system/ws","/pet-system/ws/**","/pet-system/ai/ws").uri("lb:ws://pms-core-service"))
            .route("community-api",r->r.path("/pet-system/**").uri("lb://pms-core-service"))
            .build();
    }
}
