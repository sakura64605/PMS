package com.hongjie.pms.modules.search.application.index;

import com.hongjie.pms.modules.search.api.service.SearchDataSyncService;
import com.hongjie.pms.modules.search.api.service.FullDataSyncService;
import com.hongjie.pms.modules.search.api.service.UnifiedSearchService;

import com.hongjie.pms.modules.search.infrastructure.listener.SearchDataSyncListener;
import com.hongjie.pms.modules.search.api.event.*;
import com.hongjie.pms.modules.search.api.controller.SearchController;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.entity.PetPost;
import com.hongjie.pms.modules.community.like.application.LikeChanged;
import com.hongjie.pms.modules.community.comment.api.event.CommentCreatedEvent;
import com.hongjie.pms.shared.core.enums.BizTargetType;
import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.pojo.UserInfo;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SearchIndexWiringTest {
    @Test
    void facadesOverrideEveryLegacyPublicEntrypoint() throws Exception {
        for (var pair :
                List.of(
                        List.of(SearchDataSyncService.class, PmsSearchDataSyncService.class),
                        List.of(FullDataSyncService.class, PmsFullDataSyncService.class)))
            for (var method : pair.get(0).getDeclaredMethods())
                if (java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                    assertThat(
                                    pair.get(1)
                                            .getDeclaredMethod(
                                                    method.getName(), method.getParameterTypes()))
                            .isNotNull();
    }

    private <T> void collaborator(AnnotationConfigApplicationContext context, Class<T> type) {
        context.registerBean(type, () -> mock(type));
    }

    AnnotationConfigApplicationContext context() {
        var context = new AnnotationConfigApplicationContext();
        Set<Class<?>> types = new HashSet<>();
        for (var facade : List.of(PmsSearchDataSyncService.class, PmsFullDataSyncService.class))
            types.addAll(Arrays.asList(facade.getConstructors()[0].getParameterTypes()));
        types.remove(SearchDataSyncService.class);
        for (var type : types) collaborator(context, type);
        context.register(
                PmsSearchDataSyncService.class,
                PmsFullDataSyncService.class,
                SearchIndexBootstrap.class);
        context.refresh();
        return context;
    }

    @Test
    void primaryFacadesAndOnlyOneStartupFlowAreActive() {
        try (var context = context()) {
            assertThat(context.getBean(SearchDataSyncService.class))
                    .isInstanceOf(PmsSearchDataSyncService.class);
            assertThat(context.getBean(FullDataSyncService.class))
                    .isInstanceOf(PmsFullDataSyncService.class);
            var maintenance = context.getBean(SearchMaintenance.class);
            context.publishEvent(
                    new org.springframework.boot.context.event.ApplicationReadyEvent(
                            new org.springframework.boot.SpringApplication(Object.class),
                            new String[0],
                            context,
                            Duration.ZERO));
            verify(maintenance).request();
            verifyNoMoreInteractions(maintenance);
        }
    }

    @Test
    void staleEntitiesAreReducedToIdsAndNullPublishedEntitiesAreSafe() {
        try (var context = context()) {
            var sync = context.getBean(SearchDataSyncService.class);
            var dispatch = context.getBean(SearchSyncDispatch.class);
            sync.syncPetPost(PetPost.builder().id(7L).title("untrusted stale title").build());
            sync.syncPetPost(null);
            sync.syncDailyPost(null, List.of());
            sync.syncActivity(null);
            sync.deleteById("daily_9");
            verify(dispatch).submit("pet", 7L);
            verify(dispatch).submit("daily", 9L);
            verifyNoMoreInteractions(dispatch);
            assertThatThrownBy(sync::deleteIndex).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    void searchListenersDelegateSynchronouslyToOneDispatchPath() {
        for (var method : SearchDataSyncListener.class.getDeclaredMethods())
            assertThat(method.getAnnotation(Async.class)).isNull();
    }

    @Test
    void allUpdateKindsReachAuthoritativeReload() {
        var sync = mock(SearchDataSyncService.class);
        var listener = new SearchDataSyncListener(sync);
        listener.onPetPostUpdated(new PetPostUpdatedEvent(this, 1L, "like"));
        listener.onActivityUpdated(new ActivityUpdatedEvent(this, 2L, "signup"));
        listener.onDailyPostUpdated(new DailyPostUpdatedEvent(this, 3L, "view"));
        verify(sync).syncPetPostById(1L);
        verify(sync).syncActivityById(2L);
        verify(sync).syncDailyPostById(3L);
    }

    @Test
    void counterEventsDoNotDependOnNotificationRecipients() {
        var dispatch = mock(SearchSyncDispatch.class);
        var listener = new SearchIndexDomainListener(dispatch);
        listener.liked(new LikeChanged(1L, 2L, "pet_post", null, false));
        listener.liked(new LikeChanged(1L, 3L, "daily", null, true));
        listener.liked(new LikeChanged(1L, 4L, "pet_comment", null, true));
        listener.commented(new CommentCreatedEvent(5L, BizTargetType.PET_ACTIVITY, 6L));
        verify(dispatch).submit("pet", 2L);
        verify(dispatch).submit("daily", 3L);
        verify(dispatch).submit("activity", 6L);
        verifyNoMoreInteractions(dispatch);
    }

    @Test
    void originalRebuildPathAndNewStatusEndpointRemainAdminOnly() throws Exception {
        var maintenance = mock(SearchMaintenance.class);
        when(maintenance.status())
                .thenReturn(
                        new SearchMaintenance.Status("FAILED", 3, "start", "end", "safe error", 2));
        var controller = new SearchController(mock(UnifiedSearchService.class), maintenance);
        var actor = new UserInfo();
        actor.setRole(0);
        UserContext.setUser(actor);
        try {
            assertThatThrownBy(controller::rebuildStatus).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(controller::rebuildIndex).isInstanceOf(RuntimeException.class);
            verifyNoInteractions(maintenance);
            actor.setRole(1);
            var mvc = MockMvcBuilders.standaloneSetup(controller).build();
            mvc.perform(post("/pet-system/search/rebuild")).andExpect(status().isOk());
            mvc.perform(get("/pet-system/search/rebuild/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.phase").value("FAILED"))
                    .andExpect(jsonPath("$.data.processed").value(3));
            verify(maintenance).request();
        } finally {
            UserContext.clear();
        }
    }
}
