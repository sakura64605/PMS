// DailyRecommendService.java
package com.hongjie.pms.modules.daily.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.modules.daily.dto.DailyPostDto;
import com.hongjie.pms.modules.daily.entity.DailyPost;
import com.hongjie.pms.modules.daily.mapper.DailyPostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyRecommendService {

    private final DailyRecallService recallService;
    private final DailyRankService rankService;
    private final DailyPostMapper dailyPostMapper;
    private final DailyPostService dailyPostService;
    private final DailyUserInterestService interestService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String RECOMMEND_CACHE_KEY = "daily:rec:";

    /**
     * 获取推荐流
     */
    public IPage<DailyPostDto> recommend(Long userId, Integer pageNum, Integer pageSize) {
        String cacheKey = RECOMMEND_CACHE_KEY + userId + ":" + pageNum + ":" + pageSize;

        // 查缓存
        IPage<DailyPostDto> cached = (IPage<DailyPostDto>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 召回
        Set<DailyRecallService.RecallCandidate> candidates = recallService.recall(userId, 200);
        if (candidates.isEmpty()) {
            return getDefaultRecommend(pageNum, pageSize);
        }

        // 排序
        List<DailyRecallService.RecallCandidate> ranked = rankService.rank(userId, candidates);

        // 分页
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, ranked.size());

        if (start >= ranked.size()) {
            Page<DailyPostDto> emptyPage = new Page<>(pageNum, pageSize, ranked.size());
            emptyPage.setRecords(new ArrayList<>());
            return emptyPage;
        }

        List<DailyRecallService.RecallCandidate> pageCandidates = ranked.subList(start, end);

        // 获取分页结果的详情
        List<Long> resultIds = pageCandidates.stream()
                .map(DailyRecallService.RecallCandidate::getDailyId)
                .distinct()  // 去重
                .collect(Collectors.toList());

        List<DailyPost> posts = dailyPostMapper.selectBatchIds(resultIds);
        Map<Long, DailyPost> postMap = posts.stream()
                .collect(Collectors.toMap(DailyPost::getId, p -> p, (v1, v2) -> v1));

        // 转换并去重（按ID）- 使用批量转换避免N+1查询
        List<DailyPostDto> allDtos = dailyPostService.batchConvertToDto(posts, userId);
        Map<Long, DailyPostDto> dtoMap = allDtos.stream()
                .collect(Collectors.toMap(DailyPostDto::getId, dto -> dto, (v1, v2) -> v1));

        List<DailyPostDto> result = resultIds.stream()
                .map(dtoMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                DailyPostDto::getId,
                                dto -> dto,
                                (existing, replacement) -> existing,
                                LinkedHashMap::new
                        ),
                        map -> new ArrayList<>(map.values())
                ));

        // 构建分页对象
        Page<DailyPostDto> page = new Page<>(pageNum, pageSize, ranked.size());
        page.setRecords(result);

        // 缓存
        redisTemplate.opsForValue().set(cacheKey, page, 10, TimeUnit.MINUTES);

        return page;
    }

    /**
     * 记录用户行为
     */
    public void recordAction(Long userId, Long targetId, String actionType) {
        interestService.updateInterest(userId, targetId, actionType);
        // 清除推荐缓存
        redisTemplate.delete(RECOMMEND_CACHE_KEY + userId);
    }

    /**
     * 默认推荐（新用户）
     */
    private IPage<DailyPostDto> getDefaultRecommend(Integer pageNum, Integer pageSize) {
        List<DailyPost> hotPosts = dailyPostMapper.selectList(
                new LambdaQueryWrapper<DailyPost>()
                        .eq(DailyPost::getAuditStatus, 1)
                        .orderByDesc(DailyPost::getLikeCount)
                        .last("LIMIT " + (pageNum * pageSize))
        );

        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, hotPosts.size());

        List<DailyPostDto> result = dailyPostService.batchConvertToDto(
                hotPosts.subList(start, end), UserContext.getUserId());

        Page<DailyPostDto> page = new Page<>(pageNum, pageSize, hotPosts.size());
        page.setRecords(result);
        return page;
    }
}