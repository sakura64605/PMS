package com.hongjie.pms.modules.search.application;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.hongjie.pms.contracts.search.*;
import com.hongjie.pms.searchengine.SearchEngine;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.io.IOException;

@Component
@ConditionalOnProperty(name = "pms.search.mode", havingValue = "local", matchIfMissing = true)
public class ElasticsearchSearchBackend implements SearchBackend {
    private final SearchEngine engine;

    public ElasticsearchSearchBackend(ElasticsearchClient client) {
        engine = new SearchEngine(client, SearchProtocol.INDEX_ALIAS);
    }

    static SearchQuery query(SearchInput input) {
        return new SearchQuery(
                input.keyword(),
                input.pageNum(),
                input.pageSize(),
                input.types(),
                input.sortBy(),
                input.actor());
    }

    SearchRequest request(SearchInput input) {
        return engine.request(query(input));
    }

    @Override
    public Page search(SearchInput input) {
        try {
            var result = engine.search(query(input));
            return new Page(
                    result.total(),
                    result.candidates().stream()
                            .map(c -> new Candidate(c.type(), c.id()))
                            .toList());
        } catch (IOException
                | co.elastic.clients.elasticsearch._types.ElasticsearchException failure) {
            throw new BusinessException(ErrorCode.THIRD_PARTY_ERROR);
        }
    }
}
