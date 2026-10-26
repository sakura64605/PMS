package com.hongjie.pms.aiservice;
import com.hongjie.pms.aiservice.core.AiCoreClient;
import com.hongjie.pms.modules.ai.application.tool.MyActivitiesTool;
import com.hongjie.pms.platform.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class AiCoreClientTest {
    HttpServer server;AiCoreClient client;String body;String path;String secret;int status=200;
    String reply="{\"code\":200,\"data\":{\"userId\":7,\"role\":0}}";
    @BeforeEach void start()throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/ai/v1",e->{
            path=e.getRequestURI().getPath();secret=e.getRequestHeaders().getFirst("X-PMS-Service-Token");body=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            byte[] data=reply.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(status,data.length);e.getResponseBody().write(data);e.close();
        });server.start();client=new AiCoreClient(RestClient.builder().baseUrl("http://127.0.0.1:"+server.getAddress().getPort()).build(),"test-service-token-at-least-32-characters");
    }
    @AfterEach void stop(){server.stop(0);}
    @Test void authenticatesAgainstCurrentCoreSessionAndPassesServiceIdentity(){
        assertThat(client.authenticate("Bearer user-token").getUserId()).isEqualTo(7L);
        assertThat(path).endsWith("/authenticate");assertThat(secret).isEqualTo("test-service-token-at-least-32-characters");assertThat(body).contains("Bearer user-token");
        reply="{\"code\":401,\"message\":\"revoked\"}";assertThat(client.authenticate("Bearer revoked")).isNull();
    }
    @Test void businessToolsUseAuthenticatedActorOutsideUserSuppliedArguments(){
        reply="{\"code\":200,\"data\":{\"id\":\"tool-1\",\"name\":\"my_activities\",\"result\":\"owned activities\"}}";
        var result=new MyActivitiesTool(client).execute(Map.of("userId",99,"page",2),7L);
        assertThat(result.getResult()).isEqualTo("owned activities");assertThat(path).endsWith("/tools/my_activities");assertThat(body).contains("\"actor\":7").contains("\"userId\":99");
    }
    @Test void coreOutageNeverLooksLikeEmptyDataOrAnonymousAuthentication(){
        status=503;reply="{\"code\":503}";
        assertThatThrownBy(()->client.tool("my_pets",Map.of(),7L)).isInstanceOf(BusinessException.class).hasMessageContaining("暂时不可用");
        assertThatThrownBy(()->client.authenticate("token")).isInstanceOf(BusinessException.class);
    }
    @Test void malformedOrMissingEnvelopeIsRejected(){
        reply="{\"code\":200}";assertThatThrownBy(()->client.profile(7L)).isInstanceOf(BusinessException.class);
        reply="{\"code\":403,\"message\":\"bad service identity\"}";assertThatThrownBy(()->client.tool("my_pets",Map.of(),7L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->new AiCoreClient(RestClient.create(),"short")).isInstanceOf(IllegalArgumentException.class);
    }
}
