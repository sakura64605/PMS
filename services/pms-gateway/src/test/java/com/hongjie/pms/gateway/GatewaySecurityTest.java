package com.hongjie.pms.gateway;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "pms.gateway.jwt-secret=gateway-test-key-at-least-32-characters",
    "eureka.client.enabled=false","spring.cloud.config.enabled=false"})
class GatewaySecurityTest {
    static final String SECRET="gateway-test-key-at-least-32-characters";
    static HttpServer backend;
    @Autowired WebTestClient http;
    @DynamicPropertySource static void downstream(DynamicPropertyRegistry values)throws Exception{
        backend=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        backend.createContext("/pet-system",exchange->{byte[] body="{\"code\":200}".getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});backend.start();
        values.add("spring.cloud.discovery.client.simple.instances.pms-core-service[0].uri",()->"http://127.0.0.1:"+backend.getAddress().getPort());
    }
    @AfterAll static void close(){backend.stop(0);}
    String token(long expiry)throws Exception{
        var b64=Base64.getUrlEncoder().withoutPadding();String header=b64.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload=b64.encodeToString(("{\"userId\":7,\"exp\":"+expiry+"}").getBytes(StandardCharsets.UTF_8));
        var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        String signed=header+"."+payload;return signed+"."+b64.encodeToString(mac.doFinal(signed.getBytes(StandardCharsets.UTF_8)));
    }
    @Test void publicPathsRouteThroughLoadBalancer(){http.get().uri("/pet-system/pet/list").exchange().expectStatus().isOk().expectBody().jsonPath("$.code").isEqualTo(200);}
    @Test void protectedPathsRequireValidNonExpiredSignature()throws Exception{
        http.get().uri("/pet-system/user/info").exchange().expectStatus().isUnauthorized();
        http.get().uri("/pet-system/user/info").headers(h->h.setBearerAuth("invalid")).exchange().expectStatus().isUnauthorized();
        http.get().uri("/pet-system/user/info").headers(h->h.setBearerAuth(uncheckedToken(-120))).exchange().expectStatus().isUnauthorized();
        http.get().uri("/pet-system/user/info").headers(h->h.setBearerAuth(uncheckedToken(120))).exchange().expectStatus().isOk();
    }
    String uncheckedToken(int seconds){try{return token(java.time.Instant.now().getEpochSecond()+seconds);}catch(Exception e){throw new IllegalStateException(e);}}
    @Test void signedCallerCannotReachInternalOrActuatorManagementPaths(){
        for(String path:List.of("/internal/search/v1/query","/actuator/env"))http.get().uri(path).headers(h->h.setBearerAuth(uncheckedToken(120))).exchange().expectStatus().isForbidden();
    }
    @Test void forgedTrustedHeadersAreRemovedButBearerIsPreserved(){
        var source=org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/pet-system/pet/list")
            .header("X-PMS-Service-Token","forged").header("X-PMS-Actor","1").header("Authorization","Bearer retained").build();
        var exchange=org.springframework.mock.web.server.MockServerWebExchange.from(source);
        new TrustedHeadersFilter().filter(exchange,next->{
            assertThat(next.getRequest().getHeaders().getFirst("X-PMS-Service-Token")).isNull();
            assertThat(next.getRequest().getHeaders().getFirst("X-PMS-Actor")).isNull();
            assertThat(next.getRequest().getHeaders().getFirst("Authorization")).isEqualTo("Bearer retained");return reactor.core.publisher.Mono.empty();
        }).block();
    }
}
