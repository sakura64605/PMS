package com.hongjie.pms.refactor.search;

import com.hongjie.pms.modules.search.dto.SearchResultItem;
import com.hongjie.pms.refactor.core.strategy.StrategyRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** Keep the database read transaction separate from the remote search request. */
@Component
public class SearchProjection {
    private final SearchEnrichment enrichment;
    private final StrategyRegistry<String,SearchSource> sources;
    public SearchProjection(SearchEnrichment enrichment,List<SearchSource> sources) {
        this.enrichment=enrichment;
        this.sources=new StrategyRegistry<>("search sources",sources,source->List.of(source.type()));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<SearchResultItem> load(SearchInput input,SearchBackend.Page candidates) {
        Map<String,Set<Long>> ids=new LinkedHashMap<>();
        for(var candidate:candidates.candidates()){
            if(candidate.id()==null||candidate.id()<=0||candidate.type()==null||sources.find(candidate.type()).isEmpty())continue;
            if(!input.types().isEmpty()&&!input.types().contains(candidate.type()))continue;
            ids.computeIfAbsent(candidate.type(),t->new LinkedHashSet<>()).add(candidate.id());
        }
        Map<String,Map<Long,SearchResultItem>> loaded=new HashMap<>();
        ids.forEach((type,keys)->loaded.put(type,sources.get(type).load(keys,input.actor())));
        List<SearchResultItem> items=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(var candidate:candidates.candidates()){
            if(candidate.type()==null||candidate.id()==null)continue;
            var item=loaded.getOrDefault(candidate.type(),Map.of()).get(candidate.id());
            if(item!=null&&seen.add(candidate.type()+":"+candidate.id()))items.add(item);
        }
        enrichment.enrich(items,input.actor());
        for(var item:items){
            item.setHighlightTitle(SearchHighlights.render(item.getTitle(),input.keyword(),500));
            item.setHighlightContent(SearchHighlights.render(item.getContent(),input.keyword(),150));
        }
        return items;
    }
}
