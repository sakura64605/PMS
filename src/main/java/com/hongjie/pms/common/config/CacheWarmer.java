package com.hongjie.pms.common.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.modules.activity.entity.Activity;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.notice.dto.response.NoticeDetailDto;
import com.hongjie.pms.modules.notice.entity.Notice;
import com.hongjie.pms.modules.notice.mapper.NoticeMapper;
import com.hongjie.pms.modules.notice.mapper.NoticeReadRecordMapper;
import com.hongjie.pms.modules.petpost.dto.PetDetailDto;
import com.hongjie.pms.modules.petpost.entity.PetPost;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.modules.user.dto.UserSimpleDto;
import com.hongjie.pms.common.utils.RedisScanUtil;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CacheWarmer {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisScanUtil redisScanUtil;

    @Autowired
    private NoticeMapper noticeMapper;

    @Autowired
    private NoticeReadRecordMapper noticeReadRecordMapper;

    @Autowired
    private PetPostMapper petPostMapper;

    @Autowired
    private ActivityMapper activityMapper;

    @Autowired
    private UserMapper userMapper;

    /**
     * 项目启动后预热缓存
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        log.info("========== 开始缓存预热 ==========");
        long startTime = System.currentTimeMillis();

        // 先清理可能存在的脏缓存
        cleanCorruptedCache();

        // 1. 预热公告
        warmUpNotices();

        // 2. 预热宠物分类列表
        warmUpPetCategories();

        // 3. 预热热门宠物
        warmUpHotPets();

        // 4. 预热最新宠物（首页）
        warmUpLatestPets();

        // 5. 预热推荐宠物
        warmUpRecommendedPets();

        // 6. 预热活动分类
        warmUpActivityCategories();

        // 7. 预热最新活动
        warmUpLatestActivities();

        // 8. 预热热门活动
        warmUpHotActivities();

        // 9. 预热城市列表
        warmUpCities();

        long endTime = System.currentTimeMillis();
        log.info("========== 缓存预热完成，耗时 {} ms ==========", endTime - startTime);
    }

    /**
     * 清理脏缓存（避免实体类型与DTO类型冲突）
     */
    private void cleanCorruptedCache() {
        try {
            // 清理公告缓存（使用 SCAN 替代 keys()，避免阻塞 Redis）
            long noticeDeleted = redisScanUtil.scanAndDelete("notice:*");
            if (noticeDeleted > 0) {
                log.info("清理公告缓存: {} 个", noticeDeleted);
            }

            // 清理宠物详情缓存（仅清理 pet:<数字> 格式的详情缓存，保留列表缓存）
            Set<String> petKeys = redisScanUtil.scanKeys("pet:*");
            if (!petKeys.isEmpty()) {
                Set<String> petDetailKeys = petKeys.stream()
                        .filter(key -> key.matches("pet:\\d+"))
                        .collect(Collectors.toSet());
                if (!petDetailKeys.isEmpty()) {
                    redisTemplate.delete(petDetailKeys);
                    log.info("清理宠物详情缓存: {} 个", petDetailKeys.size());
                }
            }

            // 清理活动详情缓存（仅清理 activity:<数字> 格式的详情缓存）
            Set<String> activityKeys = redisScanUtil.scanKeys("activity:*");
            if (!activityKeys.isEmpty()) {
                Set<String> activityDetailKeys = activityKeys.stream()
                        .filter(key -> key.matches("activity:\\d+"))
                        .collect(Collectors.toSet());
                if (!activityDetailKeys.isEmpty()) {
                    redisTemplate.delete(activityDetailKeys);
                    log.info("清理活动详情缓存: {} 个", activityDetailKeys.size());
                }
            }
        } catch (Exception e) {
            log.warn("清理脏缓存失败", e);
        }
    }

    /**
     * 将 Notice 转换为 NoticeDetailDto
     */
    private NoticeDetailDto convertToDetailDto(Notice notice, boolean isRead) {
        return NoticeDetailDto.builder()
                .id(notice.getId())
                .title(notice.getTitle())
                .content(notice.getContent())
                .type(notice.getType())
                .priority(notice.getPriority())
                .status(notice.getStatus())
                .isTop(notice.getIsTop())
                .publishTime(notice.getPublishTime())
                .expireTime(notice.getExpireTime())
                .createTime(notice.getCreateTime())
                .isRead(isRead)
                .build();
    }

    /**
     * 将 PetPost 转换为 PetDetailDto
     */
    private PetDetailDto convertToPetDetailDto(PetPost pet, Long currentUserId) {
        User user = userMapper.selectById(pet.getUserId());
        UserSimpleDto userSimpleDto = null;
        if (user != null) {
            userSimpleDto = UserSimpleDto.builder()
                    .userId(user.getId())
                    .username(user.getUserName())
                    .nickname(user.getNickName())
                    .avatar(user.getAvatar())
                    .build();
        }

        return PetDetailDto.builder()
                .id(pet.getId())
                .type(pet.getType())
                .title(pet.getTitle())
                .content(pet.getContent())
                .images(pet.getImages())
                .petGender(pet.getPetGender())
                .petAge(pet.getPetAge())
                .petType(pet.getPetType())
                .petName(pet.getPetName())
                .contactPhone(pet.getContactPhone())
                .contactWechat(pet.getContactWechat())
                .address(pet.getAddress())
                .viewCount(pet.getViewCount())
                .status(pet.getStatus())
                .createTime(pet.getCreateTime())
                .updateTime(pet.getUpdateTime())
                .user(userSimpleDto)
                .shareCount(pet.getShareCount())
                .commentCount(pet.getCommentCount())
                .likeCount(pet.getLikeCount())
                .build();
    }

    /**
     * 1. 预热公告（最新5条 + 全部有效公告）
     */
    private void warmUpNotices() {
        try {
            // 最新5条公告
            List<Notice> latestNotices = noticeMapper.selectList(
                    new LambdaQueryWrapper<Notice>()
                            .eq(Notice::getStatus, 1)
                            .orderByDesc(Notice::getCreateTime)
                            .last("limit 5")
            );
            if (latestNotices != null && !latestNotices.isEmpty()) {
                // 转换为 DTO 列表缓存
                List<NoticeDetailDto> latestNoticeDtos = latestNotices.stream()
                        .map(notice -> convertToDetailDto(notice, false))
                        .collect(Collectors.toList());
                redisTemplate.opsForValue().set("notice:latest", latestNoticeDtos, 10, TimeUnit.MINUTES);
                log.info("预热公告缓存: 最新{}条", latestNoticeDtos.size());
            }

            // 所有有效公告（按ID缓存）- 使用 DTO 而不是实体
            List<Notice> allNotices = noticeMapper.selectList(
                    new LambdaQueryWrapper<Notice>()
                            .eq(Notice::getStatus, 1)
            );
            for (Notice notice : allNotices) {
                String key = "notice:" + notice.getId();
                NoticeDetailDto dto = convertToDetailDto(notice, false);
                redisTemplate.opsForValue().set(key, dto, 30, TimeUnit.MINUTES);
            }
            log.info("预热公告缓存: 共{}条", allNotices.size());

        } catch (Exception e) {
            log.warn("预热公告缓存失败", e);
        }
    }

    /**
     * 2. 预热宠物分类列表
     */
    private void warmUpPetCategories() {
        try {
            // 从数据库查询所有分类（假设有category表，或从枚举中获取）
            List<String> categories = List.of("领养", "救助");
            redisTemplate.opsForValue().set("pet:categories", categories, 24, TimeUnit.HOURS);
            log.info("预热宠物分类缓存: {}个", categories.size());
        } catch (Exception e) {
            log.warn("预热宠物分类失败", e);
        }
    }

    /**
     * 3. 预热热门宠物（按浏览量）
     */
    private void warmUpHotPets() {
        try {
            List<PetPost> hotPets = petPostMapper.selectList(
                    new LambdaQueryWrapper<PetPost>()
                            .eq(PetPost::getStatus, 1)
                            .eq(PetPost::getAuditStatus, 1)
                            .orderByDesc(PetPost::getViewCount)
                            .last("limit 20")
            );

            if (hotPets != null && !hotPets.isEmpty()) {
                // 转换为 DTO 列表缓存
                List<PetDetailDto> hotPetDtos = hotPets.stream()
                        .map(pet -> convertToPetDetailDto(pet, null))
                        .collect(Collectors.toList());
                redisTemplate.opsForValue().set("pet:hot:list", hotPetDtos, 5, TimeUnit.MINUTES);
                log.info("预热热门宠物缓存: {}条", hotPetDtos.size());

                // 同时缓存每个宠物的详情 - 使用 DTO
                for (PetPost pet : hotPets) {
                    String key = "pet:" + pet.getId();
                    PetDetailDto dto = convertToPetDetailDto(pet, null);
                    redisTemplate.opsForValue().set(key, dto, 30, TimeUnit.MINUTES);
                }
            }
        } catch (Exception e) {
            log.warn("预热热门宠物失败", e);
        }
    }

    /**
     * 4. 预热最新宠物（首页）
     */
    private void warmUpLatestPets() {
        try {
            List<PetPost> latestPets = petPostMapper.selectList(
                    new LambdaQueryWrapper<PetPost>()
                            .eq(PetPost::getStatus, 1)
                            .eq(PetPost::getAuditStatus, 1)
                            .orderByDesc(PetPost::getCreateTime)
                            .last("limit 20")
            );

            if (latestPets != null && !latestPets.isEmpty()) {
                // 转换为 DTO 列表缓存（宠物列表可以使用简化的 DTO，这里用 PetListResponseDto 更合适）
                // 简化处理：直接存储宠物ID列表，或者使用专门的列表DTO
                List<Long> petIds = latestPets.stream()
                        .map(PetPost::getId)
                        .collect(Collectors.toList());
                redisTemplate.opsForValue().set("pet:latest:page:1:size:20", petIds, 2, TimeUnit.MINUTES);
                log.info("预热最新宠物缓存: {}条", latestPets.size());
            }
        } catch (Exception e) {
            log.warn("预热最新宠物失败", e);
        }
    }

    /**
     * 5. 预热推荐宠物（按点赞数）
     */
    private void warmUpRecommendedPets() {
        try {
            List<PetPost> recommendedPets = petPostMapper.selectList(
                    new LambdaQueryWrapper<PetPost>()
                            .eq(PetPost::getStatus, 1)
                            .eq(PetPost::getAuditStatus, 1)
                            .orderByDesc(PetPost::getLikeCount)
                            .last("limit 10")
            );

            if (recommendedPets != null && !recommendedPets.isEmpty()) {
                // 转换为 DTO 列表缓存
                List<PetDetailDto> recommendedPetDtos = recommendedPets.stream()
                        .map(pet -> convertToPetDetailDto(pet, null))
                        .collect(Collectors.toList());
                redisTemplate.opsForValue().set("pet:recommended", recommendedPetDtos, 10, TimeUnit.MINUTES);
                log.info("预热推荐宠物缓存: {}条", recommendedPetDtos.size());
            }
        } catch (Exception e) {
            log.warn("预热推荐宠物失败", e);
        }
    }

    /**
     * 6. 预热活动分类
     */
    private void warmUpActivityCategories() {
        try {
            List<String> categories = List.of("线下聚会", "公益活动", "领养日", "讲座培训");
            redisTemplate.opsForValue().set("activity:categories", categories, 24, TimeUnit.HOURS);
            log.info("预热活动分类缓存: {}个", categories.size());
        } catch (Exception e) {
            log.warn("预热活动分类失败", e);
        }
    }

    /**
     * 7. 预热最新活动
     */
    private void warmUpLatestActivities() {
        try {
            List<Activity> latestActivities = activityMapper.selectList(
                    new LambdaQueryWrapper<Activity>()
                            .eq(Activity::getStatus, 1)
                            .eq(Activity::getAuditStatus, 1)
                            .ge(Activity::getEndTime, java.time.LocalDateTime.now())
                            .orderByDesc(Activity::getCreateTime)
                            .last("limit 10")
            );

            if (latestActivities != null && !latestActivities.isEmpty()) {
                // 简化处理：缓存活动ID列表
                List<Long> activityIds = latestActivities.stream()
                        .map(Activity::getId)
                        .collect(Collectors.toList());
                redisTemplate.opsForValue().set("activity:latest", activityIds, 5, TimeUnit.MINUTES);
                log.info("预热最新活动缓存: {}条", latestActivities.size());
            }
        } catch (Exception e) {
            log.warn("预热最新活动失败", e);
        }
    }

    /**
     * 8. 预热热门活动（报名人数最多）
     */
    private void warmUpHotActivities() {
        try {
            List<Activity> hotActivities = activityMapper.selectList(
                    new LambdaQueryWrapper<Activity>()
                            .eq(Activity::getStatus, 1)
                            .eq(Activity::getAuditStatus, 1)
                            .ge(Activity::getEndTime, java.time.LocalDateTime.now())
                            .orderByDesc(Activity::getCurrentPeople)
                            .last("limit 10")
            );

            if (hotActivities != null && !hotActivities.isEmpty()) {
                // 简化处理：缓存活动ID列表
                List<Long> activityIds = hotActivities.stream()
                        .map(Activity::getId)
                        .collect(Collectors.toList());
                redisTemplate.opsForValue().set("activity:hot", activityIds, 5, TimeUnit.MINUTES);
                log.info("预热热门活动缓存: {}条", hotActivities.size());
            }
        } catch (Exception e) {
            log.warn("预热热门活动失败", e);
        }
    }

    /**
     * 9. 预热城市列表
     */
    private void warmUpCities() {
        try {
            // 热门城市
            List<String> hotCities = List.of("北京", "上海", "广州", "深圳", "杭州", "成都", "武汉", "西安");
            redisTemplate.opsForValue().set("city:hot", hotCities, 24, TimeUnit.HOURS);

            // 所有城市（如果有城市表就从数据库查）
            List<String> allCities = List.of("北京", "上海", "广州", "深圳", "杭州", "成都", "武汉", "西安", "南京", "苏州", "重庆", "天津");
            redisTemplate.opsForValue().set("city:all", allCities, 24, TimeUnit.HOURS);

            log.info("预热城市缓存: 热门{}个, 全部{}个", hotCities.size(), allCities.size());
        } catch (Exception e) {
            log.warn("预热城市缓存失败", e);
        }
    }
}