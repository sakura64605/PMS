package com.hongjie.pms.modules.search.listener;

import com.hongjie.pms.modules.search.event.*;
import com.hongjie.pms.modules.search.service.SearchDataSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchDataSyncListener {

    private final SearchDataSyncService syncService;

    @Async
    @EventListener
    public void onDailyPostPublished(DailyPostPublishedEvent event) {
        syncService.syncDailyPost(event.getDailyPost(), event.getTopicIds());
    }

    @Async
    @EventListener
    public void onActivityPublished(ActivityPublishedEvent event) {
        syncService.syncActivity(event.getActivity());
    }

    @Async
    @EventListener
    public void onPetPostPublished(PetPostPublishedEvent event) {
        syncService.syncPetPost(event.getPetPost());
    }

    @Async
    @EventListener
    public void onPetPostUpdated(PetPostUpdatedEvent event) {
        if ("audit".equals(event.getUpdateType()) || "content".equals(event.getUpdateType())) {
            syncService.syncPetPostById(event.getPetId());
        }
    }

    @Async
    @EventListener
    public void onActivityUpdated(ActivityUpdatedEvent event) {
        if ("audit".equals(event.getUpdateType()) || "content".equals(event.getUpdateType())) {
            syncService.syncActivityById(event.getActivityId());
        }
    }

    @Async
    @EventListener
    public void onDailyPostUpdated(DailyPostUpdatedEvent event) {
        if ("audit".equals(event.getUpdateType()) || "content".equals(event.getUpdateType())) {
            syncService.syncDailyPostById(event.getDailyId());
        }
    }
}