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
}