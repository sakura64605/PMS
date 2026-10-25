package com.hongjie.pms.modules.search.application.index;
import com.hongjie.pms.modules.search.api.service.FullDataSyncService;
import com.hongjie.pms.modules.content.daily.infrastructure.persistence.mapper.*;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
@Service @Primary
public class PmsFullDataSyncService implements FullDataSyncService {
    private final SearchMaintenance maintenance;
    private final SearchIndexCoordinator coordinator;
    public PmsFullDataSyncService(SearchMaintenance maintenance, SearchIndexCoordinator coordinator){
        this.maintenance=maintenance;this.coordinator=coordinator;
    }
    @Override public void syncAll(){maintenance.rebuildNow();}
    @Override public void syncDailyPosts(){coordinator.resync("daily");}
    @Override public void syncActivities(){coordinator.resync("activity");}
    @Override public void syncPetPosts(){coordinator.resync("pet");}
    @Override public void rebuildIndex(){maintenance.request();}
}
