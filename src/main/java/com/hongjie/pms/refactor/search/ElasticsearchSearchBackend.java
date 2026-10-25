package com.hongjie.pms.refactor.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.hongjie.pms.modules.search.document.UnifiedDoc;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.*;

@Component @RequiredArgsConstructor @Slf4j
public class ElasticsearchSearchBackend implements SearchBackend {
    SearchRequest request(SearchInput input) {
        var builder=new SearchRequest.Builder().index(com.hongjie.pms.refactor.search.index.SearchIndexStore.ALIAS)
                .from((input.pageNum()-1)*input.pageSize()).size(input.pageSize())
                .trackTotalHits(t->t.enabled(true))
                .source(s->s.filter(f->f.includes("type","businessId")));
        builder.query(q->q.bool(b->{
            b.must(m->m.multiMatch(mm->mm.query(input.keyword())
                    .fields(List.of("title^3","content","topics^2","location","petName^2","petType","address","userName"))));
            b.filter(f->f.bool(v->{
                v.should(s->s.term(t->t.field("auditStatus").value(1)));
                if(input.actor()!=null)v.should(s->s.term(t->t.field("userId").value(input.actor())));
                return v.minimumShouldMatch("1");
            }));
            if(!input.types().isEmpty())b.filter(f->f.bool(types->{
                for(String type:input.types())types.should(s->s.term(t->t.field("type").value(type)));
                return types.minimumShouldMatch("1");
            }));
            return b;
        }));
        if("time".equals(input.sortBy()))builder.sort(s->s.field(f->f.field("createTime").order(SortOrder.Desc)));
        else if("hot".equals(input.sortBy())){
            for(String field:List.of("likeCount","commentCount","viewCount","createTime"))
                builder.sort(s->s.field(f->f.field(field).order(SortOrder.Desc)));
        }else builder.sort(s->s.score(f->f.order(SortOrder.Desc)))
                .sort(s->s.field(f->f.field("createTime").order(SortOrder.Desc)));
        builder.sort(s->s.field(f->f.field("type").order(SortOrder.Asc)))
                .sort(s->s.field(f->f.field("businessId").order(SortOrder.Asc)));
        return builder.build();
    }
    @Override public Page search(SearchInput input) {
        try {
            var response=client.search(request(input),UnifiedDoc.class);
            List<Candidate> candidates=response.hits().hits().stream().map(hit->hit.source())
                    .filter(Objects::nonNull).map(doc->new Candidate(doc.getType(),doc.getBusinessId())).toList();
            return new Page(response.hits().total()==null?0:response.hits().total().value(),candidates);
        }catch(IOException|co.elastic.clients.elasticsearch._types.ElasticsearchException failure){
            log.error("Elasticsearch search failed",failure);
            throw new BusinessException(ErrorCode.THIRD_PARTY_ERROR);
        }
    }
    private final ElasticsearchClient client;
}
