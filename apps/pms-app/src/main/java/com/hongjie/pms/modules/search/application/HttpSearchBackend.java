package com.hongjie.pms.modules.search.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongjie.pms.contracts.search.*;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

@Component
@ConditionalOnProperty(name = "pms.search.mode", havingValue = "remote")
public class HttpSearchBackend implements SearchBackend {
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final String token;

    public HttpSearchBackend(
            ObjectMapper mapper,
            @Value("${pms.search.url:http://127.0.0.1:8081}") String url,
            @Value("${pms.search.token:}") String token) {
        this.mapper = mapper;
        this.token = token;
        if (token == null || token.length() < 32)
            throw new IllegalArgumentException(
                    "Remote search requires a service token of at least 32 characters");
        URI base = URI.create(url);
        if (base.getScheme() == null
                || !java.util.Set.of("http", "https").contains(base.getScheme())
                || base.getHost() == null
                || base.getUserInfo() != null
                || base.getQuery() != null
                || base.getFragment() != null)
            throw new IllegalArgumentException("Invalid search service URL");
        endpoint = base.resolve(SearchProtocol.PATH);
        client =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(1))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
    }

    @Override
    public Page search(SearchInput input) {
        try {
            var request =
                    HttpRequest.newBuilder(endpoint)
                            .timeout(Duration.ofSeconds(4))
                            .header("Content-Type", "application/json")
                            .header(SearchProtocol.TOKEN_HEADER, token)
                            .POST(
                                    HttpRequest.BodyPublishers.ofByteArray(
                                            mapper.writeValueAsBytes(
                                                    ElasticsearchSearchBackend.query(input))))
                            .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200)
                throw new java.io.IOException("Search service unavailable");
            var result = mapper.readValue(response.body(), SearchResult.class);
            return new Page(
                    result.total(),
                    result.candidates().stream()
                            .map(row -> new Candidate(row.type(), row.id()))
                            .toList());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.THIRD_PARTY_ERROR);
        } catch (java.io.IOException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.THIRD_PARTY_ERROR);
        }
    }
}
