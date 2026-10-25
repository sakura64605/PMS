package com.hongjie.pms.modules.search.application.index;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.LongConsumer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class SearchMaintenanceTest {
    SearchIndexCoordinator coordinator=mock(SearchIndexCoordinator.class);
    SearchSyncDispatch dispatch=mock(SearchSyncDispatch.class);
    @Test void statusShowsQueuedRunningCompletedAndRejectsDuplicateRequests(){
        List<Runnable> queue=new ArrayList<>();var maintenance=new SearchMaintenance(coordinator,dispatch,queue::add);
        doAnswer(inv->{((LongConsumer)inv.getArgument(0)).accept(7);return null;}).when(coordinator).rebuild(any());
        maintenance.request();assertThat(maintenance.status().phase()).isEqualTo("RUNNING");
        assertThatThrownBy(maintenance::request).isInstanceOf(RuntimeException.class);
        queue.remove(0).run();assertThat(maintenance.status().phase()).isEqualTo("SUCCEEDED");
        assertThat(maintenance.status().processed()).isEqualTo(7);assertThat(maintenance.status().finishedAt()).isNotNull();
    }
    @Test void failedRebuildIsObservableWithoutExposingUnderlyingSecretsAndCanRetry(){
        var maintenance=new SearchMaintenance(coordinator,dispatch,Runnable::run);
        doThrow(new IllegalStateException("secret internal transport details")).doNothing().when(coordinator).rebuild(any());
        assertThatThrownBy(maintenance::rebuildNow).isInstanceOf(IllegalStateException.class);
        assertThat(maintenance.status().phase()).isEqualTo("FAILED");
        assertThat(maintenance.status().error()).doesNotContain("secret");
        maintenance.rebuildNow();assertThat(maintenance.status().phase()).isEqualTo("SUCCEEDED");
    }
    @Test void executorRejectionResetsRunningFlag(){
        var maintenance=new SearchMaintenance(coordinator,dispatch,r->{throw new java.util.concurrent.RejectedExecutionException();});
        assertThatThrownBy(maintenance::request).isInstanceOf(RuntimeException.class);
        assertThat(maintenance.status().phase()).isEqualTo("FAILED");
        assertThatThrownBy(maintenance::request).isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
    }
}
