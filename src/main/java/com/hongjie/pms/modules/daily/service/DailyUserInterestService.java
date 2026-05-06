package com.hongjie.pms.modules.daily.service;

import com.hongjie.pms.modules.daily.entity.DailyUserBehavior;
import com.hongjie.pms.modules.daily.entity.DailyUserInterest;
import com.hongjie.pms.modules.daily.entity.Topic;
import com.hongjie.pms.modules.daily.mapper.DailyTopicRelMapper;
import com.hongjie.pms.modules.daily.mapper.DailyUserBehaviorMapper;
import com.hongjie.pms.modules.daily.mapper.DailyUserInterestMapper;
import com.hongjie.pms.modules.daily.mapper.TopicMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyUserInterestService {

    private final DailyUserBehaviorMapper behaviorMapper;
    private final DailyUserInterestMapper interestMapper;
    private final TopicMapper topicMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final DailyTopicRelMapper dailyTopicRelMapper;

    private static final String INTEREST_KEY = "daily:interest:";

    /**
     * 实时更新用户画像
     */
    public void updateInterest(Long userId, Long targetId, String actionType) {
        double weight = getActionWeight(actionType);

        // 获取帖子的特征（话题）
        List<Long> topicIds = dailyTopicRelMapper.getTopicIdsByDailyId(targetId);

        String redisKey = INTEREST_KEY + userId;
        for (Long topicId : topicIds) {
            String topicName = getTopicName(topicId);
            redisTemplate.opsForHash().increment(redisKey, "topic_" + topicId, weight);
            redisTemplate.opsForHash().increment(redisKey, "tag_" + topicName, weight);
        }
        redisTemplate.expire(redisKey, 7, TimeUnit.DAYS);

        // 异步更新MySQL
        asyncUpdateMysql(userId, targetId, actionType);
    }

    /**
     * 获取用户画像
     */
    public Map<String, Double> getUserInterest(Long userId) {
        String redisKey = INTEREST_KEY + userId;
        Map<Object, Object> map = redisTemplate.opsForHash().entries(redisKey);

        if (map.isEmpty()) {
            return getDefaultInterest();
        }

        Map<String, Double> result = new HashMap<>();
        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            result.put((String) entry.getKey(), ((Number) entry.getValue()).doubleValue());
        }
        return result;
    }

    /**
     * 获取用户最喜欢的TOP话题
     */
    public List<Long> getTopTopics(Long userId, int limit) {
        Map<String, Double> interest = getUserInterest(userId);

        return interest.entrySet().stream()
                .filter(e -> e.getKey().startsWith("topic_"))
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(e -> Long.parseLong(e.getKey().replace("topic_", "")))
                .collect(java.util.stream.Collectors.toList());
    }

    private double getActionWeight(String actionType) {
        switch (actionType) {
            case "like": return 1.0;
            case "share": return 1.5;
            case "view": return 0.3;
            default: return 0.1;
        }
    }

    private String getTopicName(Long topicId) {
        Topic topic = topicMapper.selectById(topicId);
        return topic != null ? topic.getName() : "";
    }

    private Map<String, Double> getDefaultInterest() {
        Map<String, Double> defaultInterest = new HashMap<>();
        defaultInterest.put("default", 0.5);
        return defaultInterest;
    }

    private void asyncUpdateMysql(Long userId, Long targetId, String actionType) {
        CompletableFuture.runAsync(() -> {
            try {
                // 1. 插入用户行为记录
                DailyUserBehavior behavior = new DailyUserBehavior();
                behavior.setUserId(userId);
                behavior.setTargetId(targetId);
                behavior.setActionType(actionType);
                behavior.setActionTime(LocalDateTime.now());
                behaviorMapper.insert(behavior);

                // 2. 同步用户兴趣画像
                String redisKey = INTEREST_KEY + userId;
                Map<Object, Object> entries = redisTemplate.opsForHash().entries(redisKey);

                if (entries.isEmpty()) {
                    return;
                }

                Map<String, Double> interestMap = new HashMap<>();
                for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                    interestMap.put((String) entry.getKey(), ((Number) entry.getValue()).doubleValue());
                }

                String interestJson = com.alibaba.fastjson2.JSON.toJSONString(interestMap);

                DailyUserInterest interest = new DailyUserInterest();
                interest.setUserId(userId);
                interest.setInterestJson(interestJson);
                interest.setUpdateTime(LocalDateTime.now());

                DailyUserInterest existing = interestMapper.selectById(userId);
                if (existing != null) {
                    interestMapper.updateById(interest);
                } else {
                    interestMapper.insert(interest);
                }
            } catch (Exception e) {
                log.error("异步更新用户兴趣到MySQL失败, userId={}", userId, e);
            }
        });
    }
}