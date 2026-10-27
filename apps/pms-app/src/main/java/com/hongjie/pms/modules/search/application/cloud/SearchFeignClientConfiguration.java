package com.hongjie.pms.modules.search.application.cloud;

import com.hongjie.pms.contracts.search.SearchProtocol;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import feign.*;

/** Deliberately not component-scanned: service credentials belong only to this Feign client. */
public class SearchFeignClientConfiguration {
    @Bean
    RequestInterceptor searchCredentials(@Value("${pms.search.token:}") String token) {
        if (token.length() < 32)
            throw new IllegalArgumentException(
                    "Cloud search requires a service token of at least 32 characters");
        return template -> template.header(SearchProtocol.TOKEN_HEADER, token);
    }

    @Bean
    Retryer searchRetryer() {
        return Retryer.NEVER_RETRY;
    }

    @Bean
    Logger.Level searchLogLevel() {
        return Logger.Level.NONE;
    }
}
