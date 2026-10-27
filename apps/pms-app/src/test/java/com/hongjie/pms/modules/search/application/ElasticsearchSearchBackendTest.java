package com.hongjie.pms.modules.search.application;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.hongjie.pms.modules.search.infrastructure.search.document.UnifiedDoc;
import com.hongjie.pms.platform.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ElasticsearchSearchBackendTest {
    ElasticsearchClient client = mock(ElasticsearchClient.class);
    ElasticsearchSearchBackend backend = new ElasticsearchSearchBackend(client);

    @Test
    void requestHasVisibilityTypesTotalTrackingAndStableSort() {
        var request =
                backend.request(SearchInput.of("cat", 2, 12, List.of("pet", "daily"), "hot", 7L));
        assertThat(request.from()).isEqualTo(12);
        assertThat(request.size()).isEqualTo(12);
        String json = request.toString();
        assertThat(json)
                .contains(
                        "auditStatus", "userId", "pet", "daily", "businessId", "track_total_hits");
        assertThat(request.sort()).hasSize(6);
    }

    @Test
    void anonymousVisibilityDoesNotIncludeOwnership() {
        String json = backend.request(SearchInput.of("cat", 1, 20, null, "time", null)).toString();
        assertThat(json).contains("auditStatus").doesNotContain("userId");
    }

    @Test
    void backendFailureIsNotReportedAsSuccessfulEmptySearch() throws Exception {
        when(client.search(
                        any(SearchRequest.class),
                        eq(com.hongjie.pms.searchengine.SearchEngine.Document.class)))
                .thenThrow(new IOException("injected unavailable"));
        assertThatThrownBy(() -> backend.search(SearchInput.of("cat", 1, 20, null, null, null)))
                .isInstanceOf(BusinessException.class);
    }
}
