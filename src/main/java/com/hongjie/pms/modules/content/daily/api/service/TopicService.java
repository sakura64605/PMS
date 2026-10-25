package com.hongjie.pms.modules.content.daily.api.service;
import com.hongjie.pms.modules.content.daily.infrastructure.persistence.entity.Topic;
import java.util.List;
/** Runtime contract implemented by the migrated service. */
public interface TopicService {
    Topic createTopic(String name, String description);
    List<Topic> getHotTopics(int limit);
    List<Topic> searchTopics(String keyword, int limit);
}
