package com.hongjie.pms.modules.search.application;
import com.hongjie.pms.modules.search.application.cloud.*;
import com.hongjie.pms.contracts.search.*;
import com.hongjie.pms.platform.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class CloudSearchBackendTest {
    @Test void feignPreservesActorAndOrderAndDoesNotFakeSuccessOnFailure(){
        var client=mock(SearchFeignClient.class);var backend=new CloudSearchBackend(client);
        when(client.query(any())).thenReturn(new SearchResult(1,List.of(new SearchResult.Candidate("pet",7L))));
        var result=backend.search(SearchInput.of("cat",1,20,null,"time",9L));assertThat(result.candidates()).containsExactly(new SearchBackend.Candidate("pet",7L));
        verify(client).query(new SearchQuery("cat",1,20,List.of(),"time",9L));
        when(client.query(any())).thenThrow(new IllegalStateException("circuit open"));
        assertThatThrownBy(()->backend.search(SearchInput.of("cat",1,20,null,"time",9L))).isInstanceOf(BusinessException.class);
    }
}
