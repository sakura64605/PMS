package com.hongjie.pms.modules.search.application.cloud;

import com.hongjie.pms.contracts.search.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "pms-search-service",
        contextId = "searchQueryClient",
        configuration = SearchFeignClientConfiguration.class)
public interface SearchFeignClient {
    @PostMapping(SearchProtocol.PATH)
    SearchResult query(@RequestBody SearchQuery query);
}
