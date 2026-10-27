package com.hongjie.pms.modules.search.application.index;

import com.hongjie.pms.modules.search.api.service.SearchDataSyncService;
import com.hongjie.pms.modules.search.infrastructure.search.document.UnifiedDoc;
import com.hongjie.pms.modules.content.daily.infrastructure.persistence.mapper.*;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.entity.PetPost;
import com.hongjie.pms.modules.activity.infrastructure.persistence.entity.Activity;
import com.hongjie.pms.modules.content.daily.infrastructure.persistence.entity.DailyPost;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@Primary
public class PmsSearchDataSyncService implements SearchDataSyncService {
    private final SearchSyncDispatch dispatch;
    private final SearchIndexCoordinator coordinator;
    private final SearchMaintenance maintenance;

    public PmsSearchDataSyncService(
            SearchSyncDispatch dispatch,
            SearchIndexCoordinator coordinator,
            SearchMaintenance maintenance) {

        this.dispatch = dispatch;
        this.coordinator = coordinator;
        this.maintenance = maintenance;
    }

    @Override
    public void syncDailyPost(DailyPost post, List<Long> topics) {
        if (post != null) syncDailyPostById(post.getId());
    }

    @Override
    public void syncPetPost(PetPost post) {
        if (post != null) syncPetPostById(post.getId());
    }

    @Override
    public void syncActivity(Activity post) {
        if (post != null) syncActivityById(post.getId());
    }

    @Override
    public void syncPetPostById(Long id) {
        dispatch.submit("pet", id);
    }

    @Override
    public void syncActivityById(Long id) {
        dispatch.submit("activity", id);
    }

    @Override
    public void syncDailyPostById(Long id) {
        dispatch.submit("daily", id);
    }

    @Override
    public void deleteById(String id) {
        if (id == null) return;
        for (String type : List.of("pet", "activity", "daily"))
            if (id.startsWith(type + "_")) {
                try {
                    dispatch.submit(type, Long.valueOf(id.substring(type.length() + 1)));
                    return;
                } catch (NumberFormatException invalid) {
                    throw new IllegalArgumentException("Invalid search document id");
                }
            }
        throw new IllegalArgumentException("Invalid search document type");
    }

    @Override
    public void batchSync(List<UnifiedDoc> docs) {
        if (docs == null) return;
        for (var doc : docs) if (doc != null) dispatch.submit(doc.getType(), doc.getBusinessId());
    }

    @Override
    public void createIndex() {
        coordinator.initialize();
    }

    @Override
    public void deleteIndex() {
        throw new UnsupportedOperationException("Use safe search index rebuild");
    }

    @Override
    public void rebuildIndex() {
        maintenance.request();
    }
}
