package com.hongjie.pms.refactor.search.index;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class SearchIndexBootstrap {
    private final SearchMaintenance maintenance;
    @EventListener(ApplicationReadyEvent.class) public void ready(){maintenance.request();}
}
