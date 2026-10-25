package com.hongjie.pms.refactor.search.index;
import com.hongjie.pms.modules.search.service.*;
import com.hongjie.pms.modules.daily.mapper.*;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
@Service @Primary
public class PmsFullDataSyncService extends FullDataSyncService {
    private final SearchMaintenance maintenance;
    private final SearchIndexCoordinator coordinator;
    public PmsFullDataSyncService(SearchDataSyncService sync,DailyPostMapper daily,ActivityMapper activities,
            PetPostMapper pets,DailyTopicRelMapper links,SearchMaintenance maintenance,SearchIndexCoordinator coordinator){
        super(sync,daily,activities,pets,links);this.maintenance=maintenance;this.coordinator=coordinator;
    }
    @Override public void syncAll(){maintenance.rebuildNow();}
    @Override public void syncDailyPosts(){coordinator.resync("daily");}
    @Override public void syncActivities(){coordinator.resync("activity");}
    @Override public void syncPetPosts(){coordinator.resync("pet");}
    @Override public void rebuildIndex(){maintenance.request();}
}
