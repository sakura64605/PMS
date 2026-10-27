package com.hongjie.pms.modules.content.daily.application;

import com.hongjie.pms.modules.content.daily.api.service.TopicService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.content.daily.infrastructure.persistence.entity.Topic;
import com.hongjie.pms.modules.content.daily.infrastructure.persistence.mapper.TopicMapper;
import com.hongjie.pms.modules.content.daily.api.service.*;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Primary
public class PmsTopicService implements TopicService {
    private final TopicMapper topics;
    private final DailyCacheInvalidator cache;

    public PmsTopicService(TopicMapper topics, DailyCacheInvalidator cache) {
        this.topics = topics;
        this.cache = cache;
    }

    @Override
    @Transactional
    public Topic createTopic(String name, String description) {
        if (UserContext.getUserId() == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        String canonical = name == null ? "" : name.strip().replaceAll("^#+|#+$", "").strip();
        if (canonical.isEmpty()
                || canonical.length() > 98
                || description != null && description.length() > 500)
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        canonical = "#" + canonical;
        if (topics.exists(new LambdaQueryWrapper<Topic>().eq(Topic::getName, canonical)))
            throw new BusinessException(ErrorCode.PARAM_ERROR, "话题已存在");
        Topic topic = new Topic();
        topic.setName(canonical);
        topic.setDescription(description);
        topic.setStatus(1);
        topic.setPostCount(0);
        topic.setViewCount(0);
        topic.setHotScore(0.0);
        topic.setCreateTime(LocalDateTime.now());
        try {
            topics.insert(topic);
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "话题已存在");
        }
        cache.allAfterCommit();
        return topic;
    }

    @Override
    public List<Topic> getHotTopics(int limit) {
        return topics.selectList(
                new LambdaQueryWrapper<Topic>()
                        .eq(Topic::getStatus, 1)
                        .orderByDesc(Topic::getHotScore)
                        .orderByDesc(Topic::getId)
                        .last("LIMIT " + Math.max(1, Math.min(50, limit))));
    }

    @Override
    public List<Topic> searchTopics(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) return List.of();
        return topics.selectList(
                new LambdaQueryWrapper<Topic>()
                        .eq(Topic::getStatus, 1)
                        .like(Topic::getName, keyword.strip())
                        .orderByDesc(Topic::getHotScore)
                        .orderByDesc(Topic::getId)
                        .last("LIMIT " + Math.max(1, Math.min(50, limit))));
    }
}
