package com.hongjie.pms.common.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.modules.activity.entity.Activity;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.notice.entity.Notice;
import com.hongjie.pms.modules.notice.mapper.NoticeMapper;
import com.hongjie.pms.modules.petpost.entity.PetPost;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CacheWarmer {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private NoticeMapper noticeMapper;

    @Autowired
    private PetPostMapper petPostMapper;

    @Autowired
    private ActivityMapper activityMapper;

    /**
     * 项目启动后预热缓存
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        log.info("========== 开始缓存预热 ==========");
        long startTime = System.currentTimeMillis();

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
                redisTemplate.opsForValue().set("notice:latest", latestNotices, 10, TimeUnit.MINUTES);
                log.info("预热公告缓存: 最新{}条", latestNotices.size());
            }

            // 所有有效公告（按ID缓存）
            List<Notice> allNotices = noticeMapper.selectList(
                    new LambdaQueryWrapper<Notice>()
                            .eq(Notice::getStatus, 1)
            );
            for (Notice notice : allNotices) {
                String key = "notice:" + notice.getId();
                redisTemplate.opsForValue().set(key, notice, 30, TimeUnit.MINUTES);
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
                            .orderByDesc(PetPost::getViewCount)
                            .last("limit 20")
            );

            if (hotPets != null && !hotPets.isEmpty()) {
                // 缓存列表
                redisTemplate.opsForValue().set("pet:hot:list", hotPets, 5, TimeUnit.MINUTES);

                // 同时缓存每个宠物的详情
                for (PetPost pet : hotPets) {
                    String key = "pet:" + pet.getId();
                    redisTemplate.opsForValue().set(key, pet, 30, TimeUnit.MINUTES);
                }
                log.info("预热热门宠物缓存: {}条", hotPets.size());
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
            // 首页第1页
            List<PetPost> latestPets = petPostMapper.selectList(
                    new LambdaQueryWrapper<PetPost>()
                            .eq(PetPost::getStatus, 1)
                            .orderByDesc(PetPost::getCreateTime)
                            .last("limit 20")
            );

            if (latestPets != null && !latestPets.isEmpty()) {
                redisTemplate.opsForValue().set("pet:latest:page:1:size:20", latestPets, 2, TimeUnit.MINUTES);
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
                            .orderByDesc(PetPost::getLikeCount)
                            .last("limit 10")
            );

            if (recommendedPets != null && !recommendedPets.isEmpty()) {
                redisTemplate.opsForValue().set("pet:recommended", recommendedPets, 10, TimeUnit.MINUTES);
                log.info("预热推荐宠物缓存: {}条", recommendedPets.size());
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
                            .ge(Activity::getEndTime, java.time.LocalDateTime.now())
                            .orderByDesc(Activity::getCreateTime)
                            .last("limit 10")
            );

            if (latestActivities != null && !latestActivities.isEmpty()) {
                redisTemplate.opsForValue().set("activity:latest", latestActivities, 5, TimeUnit.MINUTES);
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
            // 注意：这里需要关联报名表统计，简化处理
            List<Activity> hotActivities = activityMapper.selectList(
                    new LambdaQueryWrapper<Activity>()
                            .eq(Activity::getStatus, 1)
                            .ge(Activity::getEndTime, java.time.LocalDateTime.now())
                            .orderByDesc(Activity::getCurrentPeople)
                            .last("limit 10")
            );

            if (hotActivities != null && !hotActivities.isEmpty()) {
                redisTemplate.opsForValue().set("activity:hot", hotActivities, 5, TimeUnit.MINUTES);
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