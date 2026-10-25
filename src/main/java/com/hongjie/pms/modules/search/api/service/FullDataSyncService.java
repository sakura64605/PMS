package com.hongjie.pms.modules.search.api.service;
/** Runtime contract implemented by the migrated service. */
public interface FullDataSyncService {
    void syncAll();
    void syncDailyPosts();
    void syncActivities();
    void syncPetPosts();
    void rebuildIndex();
}
