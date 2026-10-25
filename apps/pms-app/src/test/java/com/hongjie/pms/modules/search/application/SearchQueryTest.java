package com.hongjie.pms.modules.search.application;

import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.search.api.dto.SearchResultItem;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SearchQueryTest {
    SearchBackend backend=mock(SearchBackend.class);
    SearchEnrichment enrichment=mock(SearchEnrichment.class);
    SearchSource pet=mock(SearchSource.class),daily=mock(SearchSource.class);
    PmsUnifiedSearchService service(){
        when(pet.type()).thenReturn("pet");when(daily.type()).thenReturn("daily");
        return new PmsUnifiedSearchService(backend,new SearchProjection(enrichment,List.of(pet,daily)));
    }
    @Test void blankSearchNormalizesDefaultsWithoutCallingBackend(){
        var result=service().search("  ",null,null,null,null,null);
        assertThat(result.getItems()).isEmpty();assertThat(result.getTotal()).isZero();
        assertThat(result.getPageNum()).isEqualTo(1);assertThat(result.getPageSize()).isEqualTo(20);
        verifyNoInteractions(backend,enrichment);
    }
    @Test void invalidInputsAreRejectedBeforeAnySearch(){
        var service=service();
        assertThatThrownBy(()->service.search("cat",0,20,null,null,null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.search("cat",1,101,null,null,null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.search("cat",Integer.MAX_VALUE,100,null,null,null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.search("cat",1,20,List.of("user"),null,null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.search("cat",1,20,Arrays.asList((String)null),null,null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.search("cat",1,20,null,"sql",null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.search("x".repeat(201),1,20,null,null,null)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(backend,enrichment);
    }
    @Test void databaseFiltersStaleCandidatesPreservesOrderAndDeduplicates(){
        var service=service();
        var a=SearchResultItem.builder().type("pet").id(1L).title("fresh cat").content("safe").build();
        var b=SearchResultItem.builder().type("daily").id(1L).title("cat").build();
        when(backend.search(any())).thenReturn(new SearchBackend.Page(9,List.of(
                new SearchBackend.Candidate("daily",1L),new SearchBackend.Candidate("pet",2L),
                new SearchBackend.Candidate("pet",1L),new SearchBackend.Candidate("pet",1L),
                new SearchBackend.Candidate("other",1L),new SearchBackend.Candidate(null,null))));
        when(pet.load(Set.of(1L,2L),7L)).thenReturn(Map.of(1L,a));
        when(daily.load(Set.of(1L),7L)).thenReturn(Map.of(1L,b));
        var result=service.search("cat",1,20,null,null,7L);
        assertThat(result.getItems()).containsExactly(b,a);
        assertThat(result.getTotal()).isEqualTo(9);assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(a.getHighlightTitle()).isEqualTo("fresh <em class='highlight'>cat</em>");
        verify(enrichment).enrich(result.getItems(),7L);
        verify(pet,times(1)).load(anySet(),eq(7L));verify(daily,times(1)).load(anySet(),eq(7L));
    }
    @Test void typeFilterIsRecheckedBeforeDatabaseLoading(){
        var service=service();
        when(backend.search(any())).thenReturn(new SearchBackend.Page(1,List.of(new SearchBackend.Candidate("daily",1L))));
        assertThat(service.search("cat",1,20,List.of("pet"),null,null).getItems()).isEmpty();
        verify(daily,never()).load(anySet(),any());verify(pet,never()).load(anySet(),any());
    }
    @Test void duplicateSourceKeysFailFast(){
        when(pet.type()).thenReturn("pet");when(daily.type()).thenReturn("pet");
        assertThatThrownBy(()->new PmsUnifiedSearchService(backend,new SearchProjection(enrichment,List.of(pet,daily)))).isInstanceOf(IllegalStateException.class);
    }
    @Test void highlightsEscapeHtmlAndTreatKeywordAsLiteral(){
        String value=SearchHighlights.render("<img onerror='bad'>c.t & CAT","c.t",150);
        assertThat(value).contains("&lt;img","&amp;","<em class='highlight'>c.t</em>").doesNotContain("<img","<em class='highlight'>CAT");
        assertThat(SearchHighlights.render("cat <script>","cat",150)).doesNotContain("<script>");
        assertThat(SearchHighlights.render(null,"cat",150)).isNull();
        assertThat(SearchHighlights.render("hello","",3)).isEqualTo("hel...");
    }
}
