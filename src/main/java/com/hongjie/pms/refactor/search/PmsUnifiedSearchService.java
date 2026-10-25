package com.hongjie.pms.refactor.search;
import com.hongjie.pms.refactor.search.api.UnifiedSearchService;

import com.hongjie.pms.modules.search.service.*;
import com.hongjie.pms.modules.search.dto.*;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import java.util.*;

@Service @Primary
public class PmsUnifiedSearchService implements UnifiedSearchService {
    private final SearchBackend backend;
    private final SearchProjection projection;
    public PmsUnifiedSearchService(SearchBackend backend, SearchProjection projection) {
        this.backend=backend;this.projection=projection;
    }
    @Override
    public UnifiedSearchResponse search(String keyword,Integer page,Integer size,List<String> types,String sort,Long actor) {
        var input=SearchInput.of(keyword,page,size,types,sort,actor);
        if(input.keyword().isEmpty())return response(input,0,List.of());
        var candidates=backend.search(input);
        return response(input,candidates.total(),projection.load(input,candidates));
    }
    private UnifiedSearchResponse response(SearchInput input,long total,List<SearchResultItem> items){
        return UnifiedSearchResponse.builder().pageNum(input.pageNum()).pageSize(input.pageSize()).total(total)
                .totalPages((int)Math.ceil((double)total/input.pageSize())).items(items).build();
    }
}
