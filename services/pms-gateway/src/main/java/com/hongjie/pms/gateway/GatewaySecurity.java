package com.hongjie.pms.gateway;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
public class GatewaySecurity {
    @Bean
    ReactiveJwtDecoder gatewayJwtDecoder(@Value("${pms.gateway.jwt-secret:}") String secret) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalArgumentException(
                    "Gateway requires the existing application's HS256 secret");
        return NimbusReactiveJwtDecoder.withSecretKey(
                        new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    SecurityWebFilterChain gatewaySecurityChain(ServerHttpSecurity security) {
        var bearer = new ServerBearerTokenAuthenticationConverter();
        return security.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(
                        auth ->
                                auth.pathMatchers("/actuator/health")
                                        .permitAll()
                                        .pathMatchers(
                                                org.springframework.http.HttpMethod.POST,
                                                "/pet-system/user/login",
                                                "/pet-system/user/register")
                                        .permitAll()
                                        .pathMatchers(
                                                org.springframework.http.HttpMethod.GET,
                                                "/pet-system/pet/list",
                                                "/pet-system/pet/{id}",
                                                "/pet-system/activity/list",
                                                "/pet-system/activity/detail/{id}",
                                                "/pet-system/notice/list",
                                                "/pet-system/notice/{id}",
                                                "/pet-system/comment/list",
                                                "/pet-system/daily/{id}",
                                                "/pet-system/daily/topics/hot",
                                                "/pet-system/daily/topics/search",
                                                "/pet-system/search/global",
                                                "/pet-system/search/suggest")
                                        .permitAll()
                                        .pathMatchers("/pet-system/**")
                                        .authenticated()
                                        .anyExchange()
                                        .denyAll())
                .oauth2ResourceServer(
                        resource ->
                                resource.bearerTokenConverter(
                                                exchange -> {
                                                    String path =
                                                            exchange.getRequest().getPath().value();
                                                    String query =
                                                            exchange.getRequest()
                                                                    .getQueryParams()
                                                                    .getFirst("token");
                                                    if ((path.equals("/pet-system/ai/ws")
                                                                    || path.startsWith(
                                                                            "/pet-system/ws"))
                                                            && query != null
                                                            && !query.isBlank())
                                                        return Mono.just(
                                                                new BearerTokenAuthenticationToken(
                                                                        query.regionMatches(
                                                                                        true, 0,
                                                                                        "Bearer ",
                                                                                        0, 7)
                                                                                ? query.substring(7)
                                                                                        .trim()
                                                                                : query.trim()));
                                                    return bearer.convert(exchange);
                                                })
                                        .jwt(jwt -> {}))
                .build();
    }
}
