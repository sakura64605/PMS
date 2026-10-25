package com.hongjie.pms.modules.search.listener;

import com.hongjie.pms.modules.search.event.*;
import com.hongjie.pms.modules.search.service.SearchDataSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchDataSyncListener {

    private final SearchDataSyncService syncService;

    @EventListener
    public void onDailyPostPublished(DailyPostPublishedEvent event) {
        syncService.syncDailyPost(event.getDailyPost(), event.getTopicIds());
    }

    @EventListener
    public void onActivityPublished(ActivityPublishedEvent event) {
        syncService.syncActivity(event.getActivity());
    }

    @EventListener
    public void onPetPostPublished(PetPostPublishedEvent event) {
        syncService.syncPetPost(event.getPetPost());
    }

    @EventListener
    public void onPetPostUpdated(PetPostUpdatedEvent event) {
        syncService.syncPetPostById(event.getPetId());
    }

    @EventListener
    public void onActivityUpdated(ActivityUpdatedEvent event) {
        syncService.syncActivityById(event.getActivityId());
    }

    @EventListener
    public void onDailyPostUpdated(DailyPostUpdatedEvent event) {
        syncService.syncDailyPostById(event.getDailyId());
    }
}