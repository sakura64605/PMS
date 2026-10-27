package com.hongjie.pms.modules.search.application.index;

import com.hongjie.pms.modules.search.infrastructure.search.document.UnifiedDoc;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SearchIndexCoordinatorTest {
    SearchIndexTestSupport.Store store = new SearchIndexTestSupport.Store();
    org.redisson.api.RedissonClient locks = SearchIndexTestSupport.locks();
    SearchIndexSource source = mock(SearchIndexSource.class);
    SearchIndexCoordinator coordinator;

    @BeforeEach
    void setup() {
        store.create("unified_search");
        when(source.type()).thenReturn("pet");
        when(source.prefix()).thenReturn("pet_");
        when(source.maxId()).thenReturn(2L);
        when(source.ids(0, 2)).thenReturn(List.of(1L, 2L));
        when(source.ids(2, 2)).thenReturn(List.of());
        when(source.load(List.of(1L, 2L)))
                .thenReturn(
                        Map.of(
                                1L,
                                doc("one"),
                                2L,
                                UnifiedDoc.builder()
                                        .id("pet_2")
                                        .businessId(2L)
                                        .type("pet")
                                        .build()));
        coordinator = new SearchIndexCoordinator(store, locks, List.of(source));
    }

    UnifiedDoc doc(String title) {
        return UnifiedDoc.builder().id("pet_1").type("pet").businessId(1L).title(title).build();
    }

    @Test
    void initializeAdoptsExistingLegacyIndexWithoutDeletingOrRebuilding() {
        coordinator.initialize();
        coordinator.initialize();
        assertThat(store.targets()).containsExactly("unified_search");
        assertThat(store.indices).hasSize(1);
    }

    @Test
    void rebuildUsesIndependentIndexAndSwitchesOnlyAfterAllBatches() {
        coordinator.initialize();
        AtomicLong progress = new AtomicLong();
        when(source.load(List.of(1L, 2L)))
                .thenAnswer(
                        inv -> {
                            assertThat(store.target).isEqualTo("unified_search");
                            return Map.of(1L, doc("fresh"));
                        });
        coordinator.rebuild(progress::addAndGet);
        assertThat(store.target).startsWith("pms_search_");
        assertThat(store.document("pet_1").getTitle()).isEqualTo("fresh");
        assertThat(store.indices).containsKey("unified_search");
        assertThat(progress.get()).isEqualTo(2);
        verify(source).ids(0, 2);
        verify(source).ids(2, 2);
    }

    @Test
    void partialBulkFailureKeepsOldAliasAndAllowsLaterRetry() {
        coordinator.initialize();
        store.failWrite = true;
        assertThatThrownBy(() -> coordinator.rebuild(n -> {}))
                .isInstanceOf(IllegalStateException.class);
        assertThat(store.target).isEqualTo("unified_search");
        store.failWrite = false;
        coordinator.rebuild(n -> {});
        assertThat(store.target).startsWith("pms_search_");
    }

    @Test
    void failedRefreshDoesNotPublishNewIndex() {
        coordinator.initialize();
        store.failRefresh = true;
        assertThatThrownBy(() -> coordinator.rebuild(n -> {}))
                .isInstanceOf(IllegalStateException.class);
        assertThat(store.target).isEqualTo("unified_search");
    }

    @Test
    void missingDatabaseRowDeletesDocumentAndNeverIndexesEventSnapshot() {
        coordinator.initialize();
        store.write(SearchIndexStore.ALIAS, List.of(doc("stale")));
        when(source.load(List.of(1L))).thenReturn(Map.of());
        coordinator.sync("pet", 1L);
        assertThat(store.document("pet_1")).isNull();
    }

    @Test
    void incrementalWaitsForRebuildThenReloadsLatestDatabaseState() throws Exception {
        coordinator.initialize();
        CountDownLatch scanning = new CountDownLatch(1),
                resume = new CountDownLatch(1),
                writerStarted = new CountDownLatch(1);
        AtomicReference<String> database = new AtomicReference<>("old");
        when(source.load(List.of(1L, 2L)))
                .thenAnswer(
                        inv -> {
                            String snapshot = database.get();
                            scanning.countDown();
                            assertThat(resume.await(5, TimeUnit.SECONDS)).isTrue();
                            return Map.of(1L, doc(snapshot));
                        });
        when(source.load(List.of(1L))).thenAnswer(inv -> Map.of(1L, doc(database.get())));
        var pool = Executors.newFixedThreadPool(2);
        try {
            var rebuild = pool.submit(() -> coordinator.rebuild(n -> {}));
            assertThat(scanning.await(5, TimeUnit.SECONDS)).isTrue();
            database.set("LATEST");
            var incremental =
                    pool.submit(
                            () -> {
                                writerStarted.countDown();
                                coordinator.sync("pet", 1L);
                            });
            assertThat(writerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(incremental.isDone()).isFalse();
            resume.countDown();
            rebuild.get(5, TimeUnit.SECONDS);
            incremental.get(5, TimeUnit.SECONDS);
            assertThat(store.document("pet_1").getTitle()).isEqualTo("LATEST");
        } finally {
            resume.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void sameDocumentCannotBeWrittenOutOfOrder() throws Exception {
        coordinator.initialize();
        CountDownLatch firstLoad = new CountDownLatch(1), resume = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(source.load(List.of(1L)))
                .thenAnswer(
                        inv -> {
                            if (calls.incrementAndGet() == 1) {
                                firstLoad.countDown();
                                assertThat(resume.await(5, TimeUnit.SECONDS)).isTrue();
                                return Map.of(1L, doc("old"));
                            }
                            return Map.of(1L, doc("LATEST"));
                        });
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> coordinator.sync("pet", 1L));
            assertThat(firstLoad.await(5, TimeUnit.SECONDS)).isTrue();
            var b = pool.submit(() -> coordinator.sync("pet", 1L));
            resume.countDown();
            a.get(5, TimeUnit.SECONDS);
            b.get(5, TimeUnit.SECONDS);
            assertThat(store.document("pet_1").getTitle()).isEqualTo("LATEST");
        } finally {
            resume.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void invalidCursorAbortsBeforeSwitchingAlias() {
        coordinator.initialize();
        when(source.ids(2, 2)).thenReturn(List.of(2L));
        assertThatThrownBy(() -> coordinator.rebuild(n -> {}))
                .isInstanceOf(IllegalStateException.class);
        assertThat(store.target).isEqualTo("unified_search");
    }
}
