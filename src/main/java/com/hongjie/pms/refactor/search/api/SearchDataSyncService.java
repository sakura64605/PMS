package com.hongjie.pms.refactor.search.api;
import com.hongjie.pms.modules.activity.entity.Activity;
import com.hongjie.pms.modules.daily.entity.DailyPost;
import com.hongjie.pms.modules.petpost.entity.PetPost;
import com.hongjie.pms.modules.search.document.UnifiedDoc;
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
