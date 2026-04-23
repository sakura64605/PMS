package com.hongjie.pms.modules.daily.service;

import com.hongjie.pms.modules.daily.entity.DailyPost;
import com.hongjie.pms.modules.daily.mapper.DailyPostMapper;
import com.hongjie.pms.modules.daily.mapper.DailyTopicRelMapper;
import com.hongjie.pms.modules.following.mapper.FollowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyRankService {

    private final DailyPostMapper dailyPostMapper;
    private final FollowMapper followMapper;
    private final DailyUserInterestService interestService;
    private final DailyTopicRelMapper dailyTopicRelMapper;

    private static final double MATCH_WEIGHT = 0.4;
    private static final double HOT_WEIGHT = 0.3;
    private static final double FRESH_WEIGHT = 0.2;
    private static final double FOLLOW_WEIGHT = 0.1;

    /**
     * 排序
     */
    public List<DailyRecallService.RecallCandidate> rank(Long userId, Set<DailyRecallService.RecallCandidate> candidates) {
        if (candidates.isEmpty()) {
            return new ArrayList<>();
        }

        // 获取用户画像
        Map<String, Double> userInterest = interestService.getUserInterest(userId);
        List<Long> followIds = followMapper.getFollowingIds(userId);

        // 批量查询帖子详情
        List<Long> dailyIds = candidates.stream()
                .map(DailyRecallService.RecallCandidate::getDailyId)
                .collect(Collectors.toList());
        List<DailyPost> posts = dailyPostMapper.selectBatchIds(dailyIds);
        Map<Long, DailyPost> postMap = posts.stream()
                .collect(Collectors.toMap(DailyPost::getId, p -> p));

        // 计算每个候选的分数
        for (DailyRecallService.RecallCandidate candidate : candidates) {
            DailyPost post = postMap.get(candidate.getDailyId());
            if (post == null) {
                candidate.setScore(0);
                continue;
            }

            double matchScore = calculateMatchScore(userInterest, post);
            double hotScore = calculateHotScore(post);
            double freshScore = calculateFreshScore(post);
            double followScore = calculateFollowScore(followIds, post);

            double totalScore = matchScore * MATCH_WEIGHT
                    + hotScore * HOT_WEIGHT
                    + freshScore * FRESH_WEIGHT
                    + followScore * FOLLOW_WEIGHT;

            candidate.setScore(totalScore);
        }

        // 按分数排序
        return candidates.stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .collect(Collectors.toList());
    }

    /**
     * 计算匹配度分数
     */
    private double calculateMatchScore(Map<String, Double> userInterest, DailyPost post) {
        // 获取帖子的话题
        List<Long> topicIds = dailyTopicRelMapper.getTopicIdsByDailyId(post.getId());
        if (topicIds.isEmpty()) {
            return 0.5;
        }

        double maxScore = 0;
        for (Long topicId : topicIds) {
            Double score = userInterest.getOrDefault("topic_" + topicId, 0.0);
            maxScore = Math.max(maxScore, score);
        }
        return Math.min(maxScore, 1.0);
    }

    /**
     * 计算热度分数
     */
    private double calculateHotScore(DailyPost post) {
        double likeScore = Math.log(post.getLikeCount() + 1) / Math.log(1000);
        double viewScore = Math.log(post.getViewCount() + 1) / Math.log(10000);
        return Math.min(likeScore * 0.6 + viewScore * 0.4, 1.0);
    }

    /**
     * 计算新鲜度分数
     */
    private double calculateFreshScore(DailyPost post) {
        long hours = ChronoUnit.HOURS.between(post.getCreateTime(), LocalDateTime.now());
        return Math.max(0, 1 - hours / 24.0);
    }

    /**
     * 计算关注分数
     */
    private double calculateFollowScore(List<Long> followIds, DailyPost post) {
        return followIds.contains(post.getUserId()) ? 1.0 : 0;
    }
}