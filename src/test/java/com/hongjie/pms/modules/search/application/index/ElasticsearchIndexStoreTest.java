package com.hongjie.pms.modules.search.application.index;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.*;
import co.elastic.clients.elasticsearch.core.*;
import com.hongjie.pms.modules.search.infrastructure.search.document.UnifiedDoc;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ElasticsearchIndexStoreTest {
    ElasticsearchClient client=mock(ElasticsearchClient.class);
    ElasticsearchIndicesClient indices=mock(ElasticsearchIndicesClient.class);
    ElasticsearchIndexStore store=new ElasticsearchIndexStore(client);
    @Test void schemaContainsSortFieldsAndCompatibleDateMappings(){
        String json=store.definition("pms_search_test").toString();
        assertThat(json).contains("businessId","strict_date_optional_time_nanos","ik_max_word","auditStatus","startTime");
    }
    @Test void bulkItemFailureIsNotReportedAsSuccess()throws Exception{
        when(client.bulk(any(BulkRequest.class))).thenReturn(BulkResponse.of(b->b.took(1).errors(true).items(List.of())));
        assertThatThrownBy(()->store.write(SearchIndexStore.ALIAS,List.of(UnifiedDoc.builder().id("pet_1").build()))).isInstanceOf(IllegalStateException.class);
        var request=ArgumentCaptor.forClass(BulkRequest.class);verify(client).bulk(request.capture());
        assertThat(request.getValue().requireAlias()).isTrue();
    }
    @Test void aliasCutoverIsOneAtomicRequestAndDoesNotDeletePhysicalIndices()throws Exception{
        when(client.indices()).thenReturn(indices);
        doCallRealMethod().when(indices).updateAliases(any(java.util.function.Function.class));
        when(indices.updateAliases(any(UpdateAliasesRequest.class))).thenReturn(UpdateAliasesResponse.of(b->b.acknowledged(true)));
        store.switchTo(List.of("old_a","old_b"),"new_c");
        var request=ArgumentCaptor.forClass(UpdateAliasesRequest.class);verify(indices).updateAliases(request.capture());
        assertThat(request.getValue().actions()).hasSize(3);
        assertThat(request.getValue().actions().get(0).isRemove()).isTrue();
        assertThat(request.getValue().actions().get(2).add().isWriteIndex()).isTrue();
        verify(indices,never()).delete(any(DeleteIndexRequest.class));
    }
}
