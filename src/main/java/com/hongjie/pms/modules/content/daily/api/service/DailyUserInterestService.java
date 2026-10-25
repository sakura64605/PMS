package com.hongjie.pms.modules.content.daily.api.service;
import java.util.List;
import java.util.Map;
/** Runtime contract implemented by the migrated service. */
public interface DailyUserInterestService {
    void updateInterest(Long userId, Long targetId, String actionType);
    Map<String, Double> getUserInterest(Long userId);
    List<Long> getTopTopics(Long userId, int limit);
}
