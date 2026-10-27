package com.hongjie.pms.modules.search.api.service;

import com.hongjie.pms.modules.activity.infrastructure.persistence.entity.Activity;
import com.hongjie.pms.modules.content.daily.infrastructure.persistence.entity.DailyPost;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.entity.PetPost;
import com.hongjie.pms.modules.search.infrastructure.search.document.UnifiedDoc;
import java.util.List;

/** Runtime contract implemented by the migrated service. */
public interface SearchDataSyncService {
    void syncDailyPost(DailyPost post, List<Long> topicIds);

    void syncActivity(Activity activity);

    void syncPetPost(PetPost petPost);

    void deleteById(String id);

    void batchSync(List<UnifiedDoc> docs);

    void syncPetPostById(Long petId);

    void syncActivityById(Long activityId);

    void syncDailyPostById(Long dailyId);

    void deleteIndex();

    void rebuildIndex();

    void createIndex();
}
