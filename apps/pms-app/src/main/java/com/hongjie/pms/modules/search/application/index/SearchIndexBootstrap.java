package com.hongjie.pms.modules.search.application.index;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "pms.search.rebuild-on-startup",
        havingValue = "true",
        matchIfMissing = true)
public class SearchIndexBootstrap {
    private final SearchMaintenance maintenance;

    @EventListener(ApplicationReadyEvent.class)
    public void ready() {
        maintenance.request();
    }
}
