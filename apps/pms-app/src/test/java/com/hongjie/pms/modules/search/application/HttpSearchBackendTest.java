package com.hongjie.pms.modules.search.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongjie.pms.contracts.search.*;
import com.hongjie.pms.platform.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class HttpSearchBackendTest {
    static final String TOKEN = "test-service-token-with-at-least-32-characters";

    @Test
    void remoteAdapterUsesVersionedContractAndPreservesActorAndOrder() throws Exception {
        var mapper = new ObjectMapper();
        var received = new AtomicReference<SearchQuery>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                SearchProtocol.PATH,
                exchange -> {
                    assertThat(exchange.getRequestHeaders().getFirst(SearchProtocol.TOKEN_HEADER))
                            .isEqualTo(TOKEN);
                    received.set(mapper.readValue(exchange.getRequestBody(), SearchQuery.class));
                    byte[] body =
                            mapper.writeValueAsBytes(
                                    new SearchResult(
                                            3,
                                            List.of(
                                                    new SearchResult.Candidate("pet", 9L),
                                                    new SearchResult.Candidate("daily", 8L))));
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                    exchange.close();
                });
        server.start();
        try {
            var remote =
                    new HttpSearchBackend(
                            mapper, "http://127.0.0.1:" + server.getAddress().getPort(), TOKEN);
            var result = remote.search(SearchInput.of("cat", 1, 20, null, "time", 7L));
            assertThat(received.get().actor()).isEqualTo(7L);
            assertThat(result.total()).isEqualTo(3);
            assertThat(result.candidates())
                    .extracting(SearchBackend.Candidate::id)
                    .containsExactly(9L, 8L);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void nonSuccessAndMalformedResponsesDoNotSilentlyUseLocalSearch() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext(
                SearchProtocol.PATH,
                exchange -> {
                    int code = calls.getAndIncrement() == 0 ? 503 : 200;
                    byte[] body = "bad response".getBytes();
                    exchange.sendResponseHeaders(code, body.length);
                    exchange.getResponseBody().write(body);
                    exchange.close();
                });
        server.start();
        try {
            var remote =
                    new HttpSearchBackend(
                            new ObjectMapper(),
                            "http://127.0.0.1:" + server.getAddress().getPort(),
                            TOKEN);
            for (int i = 0; i < 2; i++)
                assertThatThrownBy(
                                () ->
                                        remote.search(
                                                SearchInput.of("cat", 1, 20, null, "time", null)))
                        .isInstanceOf(BusinessException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingServiceCredentialFailsConfiguration() {
        assertThatThrownBy(
                        () ->
                                new HttpSearchBackend(
                                        new ObjectMapper(), "http://127.0.0.1:8081", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void configurationSelectsExactlyOneBackendAndRejectsMissingToken() {
        var runner =
                new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                        .withBean(ObjectMapper.class, ObjectMapper::new)
                        .withBean(
                                co.elastic.clients.elasticsearch.ElasticsearchClient.class,
                                () ->
                                        org.mockito.Mockito.mock(
                                                co.elastic.clients.elasticsearch.ElasticsearchClient
                                                        .class))
                        .withUserConfiguration(
                                HttpSearchBackend.class, ElasticsearchSearchBackend.class);
        runner.run(
                context ->
                        assertThat(context)
                                .hasSingleBean(SearchBackend.class)
                                .hasSingleBean(ElasticsearchSearchBackend.class)
                                .doesNotHaveBean(HttpSearchBackend.class));
        runner.withPropertyValues("pms.search.mode=remote", "pms.search.token=" + TOKEN)
                .run(
                        context ->
                                assertThat(context)
                                        .hasSingleBean(SearchBackend.class)
                                        .hasSingleBean(HttpSearchBackend.class)
                                        .doesNotHaveBean(ElasticsearchSearchBackend.class));
        runner.withPropertyValues("pms.search.mode=remote", "pms.search.token=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void serviceTimeoutIsBounded() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                SearchProtocol.PATH,
                exchange -> {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        exchange.close();
                    }
                });
        server.start();
        try {
            var remote =
                    new HttpSearchBackend(
                            new ObjectMapper(),
                            "http://127.0.0.1:" + server.getAddress().getPort(),
                            TOKEN);
            long started = System.nanoTime();
            assertThatThrownBy(
                            () -> remote.search(SearchInput.of("cat", 1, 20, null, "time", null)))
                    .isInstanceOf(BusinessException.class);
            assertThat(java.time.Duration.ofNanos(System.nanoTime() - started))
                    .isLessThan(java.time.Duration.ofSeconds(5));
        } finally {
            server.stop(0);
        }
    }
}
