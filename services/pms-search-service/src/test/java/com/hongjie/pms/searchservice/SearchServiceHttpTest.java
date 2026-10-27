package com.hongjie.pms.searchservice;

import com.hongjie.pms.contracts.search.*;
import com.hongjie.pms.searchengine.SearchEngine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import java.io.IOException;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "pms.search-service.token=test-service-token-with-at-least-32-characters",
            "pms.search-service.elasticsearch-url=http://127.0.0.1:1",
            "management.health.elasticsearch.enabled=false"
        })
class SearchServiceHttpTest {
    @Autowired TestRestTemplate http;
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @MockBean SearchEngine engine;

    HttpHeaders headers() {
        var h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set(SearchProtocol.TOKEN_HEADER, "test-service-token-with-at-least-32-characters");
        return h;
    }

    @Test
    void unauthenticatedCallerCannotImpersonateAnActor() throws Exception {
        var query = new SearchQuery("cat", 1, 20, List.of(), "time", 7L);
        var request =
                java.net.http.HttpRequest.newBuilder(
                                java.net.URI.create(
                                        "http://127.0.0.1:" + port + SearchProtocol.PATH))
                        .header("Content-Type", "application/json")
                        .POST(
                                java.net.http.HttpRequest.BodyPublishers.ofString(
                                        new com.fasterxml.jackson.databind.ObjectMapper()
                                                .writeValueAsString(query)))
                        .build();
        assertThat(
                        java.net.http.HttpClient.newHttpClient()
                                .send(request, java.net.http.HttpResponse.BodyHandlers.discarding())
                                .statusCode())
                .isEqualTo(401);
        verifyNoInteractions(engine);
    }

    @Test
    void authenticatedQueryPreservesCandidateOrderAndActor() throws Exception {
        var query = new SearchQuery("cat", 1, 20, List.of("pet"), "hot", 7L);
        when(engine.search(query))
                .thenReturn(
                        new SearchResult(
                                2,
                                List.of(
                                        new SearchResult.Candidate("pet", 9L),
                                        new SearchResult.Candidate("pet", 8L))));
        var response =
                http.postForEntity(
                        SearchProtocol.PATH,
                        new HttpEntity<>(query, headers()),
                        SearchResult.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().candidates())
                .extracting(SearchResult.Candidate::id)
                .containsExactly(9L, 8L);
        verify(engine).search(query);
    }

    @Test
    void invalidRequestIsRejectedAndUpstreamFailureIs503() throws Exception {
        var invalid =
                http.postForEntity(
                        SearchProtocol.PATH,
                        new HttpEntity<>(
                                "{\"keyword\":\"cat\",\"pageNum\":0,\"pageSize\":20,\"types\":[],\"sortBy\":\"time\"}",
                                headers()),
                        String.class);
        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(engine);
        when(engine.search(any())).thenThrow(new IOException("unavailable"));
        var valid = new SearchQuery("cat", 1, 20, List.of(), "time", null);
        assertThat(
                        http.postForEntity(
                                        SearchProtocol.PATH,
                                        new HttpEntity<>(valid, headers()),
                                        String.class)
                                .getStatusCode()
                                .value())
                .isEqualTo(503);
    }

    @Test
    void healthHasNoBusinessDatabaseDependency() {
        assertThat(http.getForEntity("/actuator/health", String.class).getStatusCode().value())
                .isEqualTo(200);
    }
}
