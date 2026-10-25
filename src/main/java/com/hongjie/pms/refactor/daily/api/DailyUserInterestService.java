package com.hongjie.pms.refactor.daily.api;
import java.util.List;
import java.util.Map;
/** Runtime contract implemented by the migrated service. */
public interface DailyUserInterestService {
    void updateInterest(Long userId, Long targetId, String actionType);
    Map<String, Double> getUserInterest(Long userId);
    List<Long> getTopTopics(Long userId, int limit);
}
