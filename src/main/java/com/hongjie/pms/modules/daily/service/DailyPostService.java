package com.hongjie.pms.modules.daily.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.enums.PostType;
import com.hongjie.pms.common.enums.TargetType;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.audit.service.AuditService;
import com.hongjie.pms.modules.daily.dto.DailyPostDto;
import com.hongjie.pms.modules.daily.dto.PublishDailyRequest;
import com.hongjie.pms.modules.daily.dto.TopicDto;
import com.hongjie.pms.modules.daily.entity.DailyPost;
import com.hongjie.pms.modules.daily.entity.DailyTopicRel;
import com.hongjie.pms.modules.daily.entity.Topic;
import com.hongjie.pms.modules.daily.mapper.DailyPostMapper;
import com.hongjie.pms.modules.daily.mapper.DailyTopicRelMapper;
import com.hongjie.pms.modules.daily.mapper.TopicMapper;
import com.hongjie.pms.modules.following.mapper.FollowMapper;
import com.hongjie.pms.modules.like.entity.LikeRecord;
import com.hongjie.pms.modules.like.mapper.LikeRecordMapper;
import com.hongjie.pms.modules.user.dto.UserSimpleDto;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyPostService {

    private final DailyPostMapper dailyPostMapper;
    private final TopicMapper topicMapper;
    private final DailyTopicRelMapper dailyTopicRelMapper;
    private final UserMapper userMapper;
    private final LikeRecordMapper likeRecordMapper;
    private final FollowMapper followMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AuditService auditService;

    private static final String RECOMMEND_KEY = "rec:daily:";

    /**
     * 发布日常
     */
    @Transactional
    public DailyPostDto publish(PublishDailyRequest request) {
        Long userId = UserContext.getUserId();

        DailyPost dailyPost = request.getDailyPost();
        List<Long> topicIds = request.getTopicIds();

        dailyPost.setUserId(userId);
        dailyPost.setAuditStatus(0);
        dailyPost.setStatus(1);
        dailyPost.setCreateTime(LocalDateTime.now());

        // 确保 content 不为 null
        if (dailyPost.getContent() == null) {
            dailyPost.setContent("");
        }

        dailyPostMapper.insert(dailyPost);

        // 关联话题
        if (topicIds != null && !topicIds.isEmpty()) {
            for (Long topicId : topicIds) {
                DailyTopicRel rel = new DailyTopicRel();
                rel.setDailyId(dailyPost.getId());
                rel.setTopicId(topicId);
                dailyTopicRelMapper.insert(rel);
                topicMapper.incrementPostCount(topicId);
            }
        }

        auditService.submit(TargetType.DAILY.getCode(), dailyPost.getId());

        // 清除推荐缓存
        redisTemplate.delete(RECOMMEND_KEY + userId);

        return convertToDto(dailyPost, userId);
    }

    /**
     * 推荐流
     */
    @Deprecated
    public IPage<DailyPostDto> getRecommendFeed(Integer pageNum, Integer pageSize) {
        Long userId = UserContext.getUserId();
        String cacheKey = RECOMMEND_KEY + userId + ":" + pageNum;

        // 查缓存
        List<DailyPostDto> cached = (List<DailyPostDto>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            Page<DailyPostDto> page = new Page<>(pageNum, pageSize, 0);
            page.setRecords(cached);
            return page;
        }

        // 获取关注的用户ID
        List<Long> followIds = followMapper.getFollowingIds(userId);

        // 多路召回
        Set<DailyPost> candidates = new HashSet<>();

        // 关注召回
        if (!followIds.isEmpty()) {
            List<DailyPost> followPosts = dailyPostMapper.selectList(
                    new LambdaQueryWrapper<DailyPost>()
                            .in(DailyPost::getUserId, followIds)
                            .eq(DailyPost::getAuditStatus, 1)
                            .orderByDesc(DailyPost::getCreateTime)
                            .last("LIMIT 50")
            );
            candidates.addAll(followPosts);
        }

        // 热门召回
        List<DailyPost> hotPosts = dailyPostMapper.selectList(
                new LambdaQueryWrapper<DailyPost>()
                        .eq(DailyPost::getAuditStatus, 1)
                        .orderByDesc(DailyPost::getLikeCount)
                        .orderByDesc(DailyPost::getViewCount)
                        .last("LIMIT 50")
        );
        candidates.addAll(hotPosts);

        // 新鲜召回
        List<DailyPost> freshPosts = dailyPostMapper.selectList(
                new LambdaQueryWrapper<DailyPost>()
                        .eq(DailyPost::getAuditStatus, 1)
                        .orderByDesc(DailyPost::getCreateTime)
                        .last("LIMIT 30")
        );
        candidates.addAll(freshPosts);

        // 排序
        List<DailyPost> sorted = candidates.stream()
                .sorted((a, b) -> {
                    double scoreA = calculateScore(a, followIds);
                    double scoreB = calculateScore(b, followIds);
                    return Double.compare(scoreB, scoreA);
                })
                .collect(Collectors.toList());

        // 分页
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, sorted.size());
        List<DailyPost> pageList = sorted.subList(start, end);

        // 转换
        List<DailyPostDto> result = pageList.stream()
                .map(post -> convertToDto(post, userId))
                .collect(Collectors.toList());

        // 缓存1分钟
        redisTemplate.opsForValue().set(cacheKey, result, 1, TimeUnit.MINUTES);

        Page<DailyPostDto> page = new Page<>(pageNum, pageSize, sorted.size());
        page.setRecords(result);
        return page;
    }

    /**
     * 计算分数
     */
    @Deprecated
    private double calculateScore(DailyPost post, List<Long> followIds) {
        double score = 0;
        if (followIds.contains(post.getUserId())) {
            score += 10;
        }
        score += Math.log(post.getLikeCount() + 1) * 0.5;
        long hours = java.time.Duration.between(post.getCreateTime(), LocalDateTime.now()).toHours();
        double freshness = Math.max(0, 1 - hours / 24.0);
        score += freshness * 5;
        return score;
    }

    /**
     * 获取详情
     */
    public DailyPostDto getDetail(Long id) {
        Long userId = UserContext.getUserId();
        DailyPost post = dailyPostMapper.selectById(id);
        if (post == null || post.getStatus() == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "内容不存在");
        }
        dailyPostMapper.incrementViewCount(id);
        return convertToDto(post, userId);
    }

    /**
     * 点赞/取消点赞
     */
    @Transactional
    public boolean like(Long id) {
        Long userId = UserContext.getUserId();

        LikeRecord likeRecord = likeRecordMapper.selectOne(
                new LambdaQueryWrapper<LikeRecord>()
                        .eq(LikeRecord::getUserId, userId)
                        .eq(LikeRecord::getTargetId, id)
                        .eq(LikeRecord::getTargetType, "daily")
        );

        redisTemplate.delete(RECOMMEND_KEY + userId);

        if (likeRecord != null) {
            likeRecordMapper.deleteById(likeRecord);
            dailyPostMapper.decrementLikeCount(id);
            return false;
        } else {
            LikeRecord record = new LikeRecord();
            record.setUserId(userId);
            record.setTargetId(id);
            record.setTargetType("daily");
            record.setCreateTime(LocalDateTime.now());
            likeRecordMapper.insert(record);
            dailyPostMapper.incrementLikeCount(id);
            return true;
        }

    }

    /**
     * 删除日常
     */
    @Transactional
    public void delete(Long id) {
        Long userId = UserContext.getUserId();
        DailyPost post = dailyPostMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "内容不存在");
        }
        if (!post.getUserId().equals(userId) && !UserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权删除");
        }
        post.setStatus(0);
        dailyPostMapper.updateById(post);
    }

    /**
     * 转换DTO
     */
    public DailyPostDto convertToDto(DailyPost post, Long currentUserId) {
        User user = userMapper.selectById(post.getUserId());
        UserSimpleDto userDto = UserSimpleDto.builder()
                .userId(user.getId())
                .username(user.getUserName())
                .nickname(user.getNickName())
                .avatar(user.getAvatar())
                .build();

        // 是否点赞
        LikeRecord likeRecord = likeRecordMapper.selectOne(
                new LambdaQueryWrapper<LikeRecord>()
                        .eq(LikeRecord::getUserId, currentUserId)
                        .eq(LikeRecord::getTargetId, post.getId())
                        .eq(LikeRecord::getTargetType, "daily")
        );

        // 是否关注
        boolean isFollowed = followMapper.exists(
                new LambdaQueryWrapper<com.hongjie.pms.modules.following.entity.Follow>()
                        .eq(com.hongjie.pms.modules.following.entity.Follow::getFollowerId, currentUserId)
                        .eq(com.hongjie.pms.modules.following.entity.Follow::getFollowingId, post.getUserId())
        );

        // 获取话题
        List<Long> topicIds = dailyTopicRelMapper.getTopicIdsByDailyId(post.getId());
        List<TopicDto> topics = new ArrayList<>();
        if (!topicIds.isEmpty()) {
            List<Topic> topicList = topicMapper.selectBatchIds(topicIds);
            topics = topicList.stream()
                    .map(t -> TopicDto.builder()
                            .id(t.getId())
                            .name(t.getName())
                            .description(t.getDescription())
                            .postCount(t.getPostCount())
                            .hotScore(t.getHotScore())
                            .build())
                    .collect(Collectors.toList());
        }

        return DailyPostDto.builder()
                .id(post.getId())
                .content(post.getContent())
                .images(post.getImages())
                .videoUrl(post.getVideoUrl())
                .location(post.getLocation())
                .user(userDto)
                .viewCount(post.getViewCount())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .isLiked(likeRecord != null)
                .isFollowed(isFollowed)
                .topics(topics)
                .createTime(post.getCreateTime())
                .build();
    }
}