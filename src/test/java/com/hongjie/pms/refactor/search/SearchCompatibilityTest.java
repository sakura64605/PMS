package com.hongjie.pms.refactor.search;
import com.hongjie.pms.refactor.search.api.SearchDataSyncService;
import com.hongjie.pms.refactor.search.api.UnifiedSearchService;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.hongjie.pms.modules.search.controller.SearchController;
import com.hongjie.pms.modules.search.dto.*;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.UserInfo;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SearchCompatibilityTest {
    @Test void existingSearchServiceInjectionSelectsPrimaryImplementation(){
        try(var context=new AnnotationConfigApplicationContext()){
            context.registerBean(ElasticsearchClient.class,()->mock(ElasticsearchClient.class));
            context.registerBean(SearchDataSyncService.class,()->mock(SearchDataSyncService.class));
            context.registerBean(SearchBackend.class,()->mock(SearchBackend.class));
            context.registerBean(SearchProjection.class,()->mock(SearchProjection.class));
            context.register(PmsUnifiedSearchService.class);context.refresh();
            assertThat(context.getBean(UnifiedSearchService.class)).isInstanceOf(PmsUnifiedSearchService.class);
        }
    }
    @Test void globalEndpointKeepsRequestNamesAndResponseShape()throws Exception{
        var service=mock(UnifiedSearchService.class);
        when(service.search("cat",1,12,List.of("pet"),"time",7L))
                .thenReturn(UnifiedSearchResponse.builder().total(1L).pageNum(1).pageSize(12).totalPages(1)
                        .items(List.of(SearchResultItem.builder().id(9L).type("pet").title("cat").isLiked(true).isFavorite(false).build())).build());
        var controller=new SearchController(service,mock(com.hongjie.pms.refactor.search.index.SearchMaintenance.class));
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        var actor=new UserInfo();actor.setUserId(7L);UserContext.setUser(actor);
        try{
            mvc.perform(get("/pet-system/search/global").param("keyword","cat").param("pageSize","12").param("types","pet").param("sortBy","time"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.pageNum").value(1)).andExpect(jsonPath("$.data.items[0].type").value("pet"))
                    .andExpect(jsonPath("$.data.items[0].isLiked").value(true));
            verify(service).search("cat",1,12,List.of("pet"),"time",7L);
        }finally{UserContext.clear();}
    }
}
