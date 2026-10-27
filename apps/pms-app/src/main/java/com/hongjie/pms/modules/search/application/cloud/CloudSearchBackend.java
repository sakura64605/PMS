package com.hongjie.pms.modules.search.application.cloud;

import com.hongjie.pms.modules.search.application.*;
import com.hongjie.pms.contracts.search.SearchQuery;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.platform.enums.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(name = "pms.search.mode", havingValue = "cloud")
public class CloudSearchBackend implements SearchBackend {
    private final SearchFeignClient client;

    public CloudSearchBackend(SearchFeignClient client) {
        this.client = client;
    }

    @Override
    public Page search(SearchInput input) {
        try {
            var result =
                    client.query(
                            new SearchQuery(
                                    input.keyword(),
                                    input.pageNum(),
                                    input.pageSize(),
                                    input.types(),
                                    input.sortBy(),
                                    input.actor()));
            return new Page(
                    result.total(),
                    result.candidates().stream()
                            .map(row -> new Candidate(row.type(), row.id()))
                            .toList());
        } catch (RuntimeException unavailable) {
            throw new BusinessException(ErrorCode.THIRD_PARTY_ERROR);
        }
    }
}
