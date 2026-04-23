package com.hongjie.pms.modules.daily.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.daily.entity.Topic;
import com.hongjie.pms.modules.daily.mapper.TopicMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicService {

    private final TopicMapper topicMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String HOT_TOPIC_KEY = "topic:hot";

    public Topic createTopic(String name, String description) {
        LambdaQueryWrapper<Topic> wrapper = new LambdaQueryWrapper<Topic>()
                .eq(Topic::getName, name);
        Topic exist = topicMapper.selectOne(wrapper);
        if (exist != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "话题已存在");
        }

        Topic topic = new Topic();
        topic.setName(name.startsWith("#") ? name : "#" + name);
        topic.setDescription(description);
        topic.setPostCount(0);
        topic.setViewCount(0);
        topic.setHotScore(0.0);
        topic.setStatus(1);
        topicMapper.insert(topic);

        redisTemplate.delete(HOT_TOPIC_KEY);
        return topic;
    }

    public List<Topic> getHotTopics(int limit) {
        List<Topic> cached = (List<Topic>) redisTemplate.opsForValue().get(HOT_TOPIC_KEY);
        if (cached != null && !cached.isEmpty()) {
            return cached.stream().limit(limit).collect(java.util.stream.Collectors.toList());
        }

        List<Topic> hotTopics = topicMapper.getHotTopics(50);
        redisTemplate.opsForValue().set(HOT_TOPIC_KEY, hotTopics, 5, TimeUnit.MINUTES);

        return hotTopics.stream().limit(limit).collect(java.util.stream.Collectors.toList());
    }

    public List<Topic> searchTopics(String keyword, int limit) {
        return topicMapper.searchTopics(keyword, limit);
    }
}