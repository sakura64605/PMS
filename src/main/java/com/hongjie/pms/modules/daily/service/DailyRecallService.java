package com.hongjie.pms.modules.daily.service;

import com.hongjie.pms.modules.daily.entity.DailyPost;
import com.hongjie.pms.modules.daily.mapper.DailyPostMapper;
import com.hongjie.pms.modules.daily.mapper.DailyTopicRelMapper;
import com.hongjie.pms.modules.following.mapper.FollowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyRecallService {

    private final DailyPostMapper dailyPostMapper;
    private final DailyTopicRelMapper dailyTopicRelMapper;
    private final FollowMapper followMapper;
    private final DailyUserInterestService interestService;

    /**
     * 多路召回
     */
    public Set<RecallCandidate> recall(Long userId, int limit) {
        // 使用 LinkedHashMap 去重（按 dailyId）
        Map<Long, RecallCandidate> candidatesMap = new LinkedHashMap<>();

        // 1. 兴趣召回
        interestRecall(userId, limit / 2).forEach(c -> candidatesMap.putIfAbsent(c.getDailyId(), c));

        // 2. 关注召回
        followRecall(userId, limit / 2).forEach(c -> candidatesMap.putIfAbsent(c.getDailyId(), c));

        // 3. 热度召回
        hotRecall(limit / 2).forEach(c -> candidatesMap.putIfAbsent(c.getDailyId(), c));

        // 4. 新鲜召回
        freshRecall(limit / 3).forEach(c -> candidatesMap.putIfAbsent(c.getDailyId(), c));

        return new HashSet<>(candidatesMap.values());
    }

    /**
     * 兴趣召回：根据用户喜欢的话题推荐
     */
    private Set<RecallCandidate> interestRecall(Long userId, int limit) {
        Set<RecallCandidate> result = new HashSet<>();

        List<Long> topTopics = interestService.getTopTopics(userId, 3);
        for (Long topicId : topTopics) {
            List<DailyPost> posts = dailyPostMapper.getPostsByTopic(topicId, limit);
            for (DailyPost post : posts) {
                result.add(new RecallCandidate(post.getId(), 0.8, "interest"));
            }
            if (result.size() >= limit) break;
        }

        return result;
    }

    /**
     * 关注召回：关注的人发布的帖子
     */
    private Set<RecallCandidate> followRecall(Long userId, int limit) {
        Set<RecallCandidate> result = new HashSet<>();

        List<Long> followIds = followMapper.getFollowingIds(userId);
        if (followIds.isEmpty()) {
            return result;
        }

        List<DailyPost> posts = dailyPostMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DailyPost>()
                        .in(DailyPost::getUserId, followIds)
                        .eq(DailyPost::getAuditStatus, 1)
                        .orderByDesc(DailyPost::getCreateTime)
                        .last("LIMIT " + limit)
        );

        for (DailyPost post : posts) {
            result.add(new RecallCandidate(post.getId(), 0.9, "follow"));
        }
        return result;
    }

    /**
     * 热度召回
     */
    private Set<RecallCandidate> hotRecall(int limit) {
        Set<RecallCandidate> result = new HashSet<>();

        List<DailyPost> hotPosts = dailyPostMapper.getHotPosts(limit);
        for (DailyPost post : hotPosts) {
            result.add(new RecallCandidate(post.getId(), 0.5, "hot"));
        }
        return result;
    }

    /**
     * 新鲜召回
     */
    private Set<RecallCandidate> freshRecall(int limit) {
        Set<RecallCandidate> result = new HashSet<>();

        List<DailyPost> freshPosts = dailyPostMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DailyPost>()
                        .eq(DailyPost::getAuditStatus, 1)
                        .orderByDesc(DailyPost::getCreateTime)
                        .last("LIMIT " + limit)
        );

        for (DailyPost post : freshPosts) {
            result.add(new RecallCandidate(post.getId(), 0.4, "fresh"));
        }
        return result;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class RecallCandidate {
        private Long dailyId;
        private double score;
        private String source;
    }
}