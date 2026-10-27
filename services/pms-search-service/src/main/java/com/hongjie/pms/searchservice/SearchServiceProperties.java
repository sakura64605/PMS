package com.hongjie.pms.searchservice;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("pms.search-service")
public record SearchServiceProperties(String token, String elasticsearchUrl) {
    public SearchServiceProperties {
        if (token == null || token.length() < 32)
            throw new IllegalArgumentException(
                    "Search service requires a service token of at least 32 characters");
        if (elasticsearchUrl == null || elasticsearchUrl.isBlank())
            throw new IllegalArgumentException("Elasticsearch URL is required");
    }
}
