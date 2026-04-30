package com.hongjie.pms.modules.search.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.modules.activity.entity.Activity;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.daily.entity.DailyPost;
import com.hongjie.pms.modules.daily.mapper.DailyPostMapper;
import com.hongjie.pms.modules.daily.mapper.DailyTopicRelMapper;
import com.hongjie.pms.modules.petpost.entity.PetPost;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class FullDataSyncService {

    private final SearchDataSyncService syncService;
    private final DailyPostMapper dailyPostMapper;
    private final ActivityMapper activityMapper;
    private final PetPostMapper petPostMapper;
    private final DailyTopicRelMapper dailyTopicRelMapper;

    /**
     * 全量同步所有数据
     */
    @Async
    // @EventListener(ApplicationReadyEvent.class)
    public void syncAll() {
        log.info("========== 开始全量同步所有数据到ES ==========");
        long startTime = System.currentTimeMillis();

        syncDailyPosts();
        syncActivities();
        syncPetPosts();

        long endTime = System.currentTimeMillis();
        log.info("========== 全量同步完成，总耗时: {} ms ==========", (endTime - startTime));
    }

    /**
     * 同步所有日记（修复版）
     */
    public void syncDailyPosts() {
        log.info("========== 开始同步日记数据 ==========");
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        int pageSize = 100;  // 减小批次大小，避免内存问题
        int currentPage = 1;

        while (true) {
            try {
                Page<DailyPost> page = new Page<>(currentPage, pageSize);
                Page<DailyPost> dailyPage = dailyPostMapper.selectPage(page, null);

                List<DailyPost> dailyPosts = dailyPage.getRecords();
                if (dailyPosts == null || dailyPosts.isEmpty()) {
                    break;
                }

                for (DailyPost post : dailyPosts) {
                    try {
                        List<Long> topicIds = dailyTopicRelMapper.getTopicIdsByDailyId(post.getId());
                        syncService.syncDailyPost(post, topicIds);
                        successCount.incrementAndGet();
                        log.debug("同步日记成功: id={}", post.getId());
                    } catch (Exception e) {
                        log.error("同步日记失败: id={}, error={}", post.getId(), e.getMessage(), e);
                        failCount.incrementAndGet();
                    }
                }

                log.info("日记同步进度: 已处理 {} 条, 成功={}, 失败={}",
                        successCount.get() + failCount.get(), successCount.get(), failCount.get());

                if (dailyPage.getCurrent() >= dailyPage.getPages()) {
                    break;
                }
                currentPage++;

            } catch (Exception e) {
                log.error("分页查询日记失败: page={}", currentPage, e);
                break;
            }
        }

        log.info("日记同步完成: 总处理={}, 成功={}, 失败={}",
                successCount.get() + failCount.get(), successCount.get(), failCount.get());
    }

    /**
     * 同步所有活动（修复版）
     */
    public void syncActivities() {
        log.info("========== 开始同步活动数据 ==========");
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        int pageSize = 100;
        int currentPage = 1;

        while (true) {
            try {
                Page<Activity> page = new Page<>(currentPage, pageSize);
                Page<Activity> activityPage = activityMapper.selectPage(page, null);

                List<Activity> activities = activityPage.getRecords();
                if (activities == null || activities.isEmpty()) {
                    break;
                }

                for (Activity activity : activities) {
                    try {
                        syncService.syncActivity(activity);
                        successCount.incrementAndGet();
                        log.debug("同步活动成功: id={}", activity.getId());
                    } catch (Exception e) {
                        log.error("同步活动失败: id={}, error={}", activity.getId(), e.getMessage(), e);
                        failCount.incrementAndGet();  // ✅ 关键修复：增加失败计数
                    }
                }

                log.info("活动同步进度: 已处理 {} 条, 成功={}, 失败={}",
                        successCount.get() + failCount.get(), successCount.get(), failCount.get());

                if (activityPage.getCurrent() >= activityPage.getPages()) {
                    break;
                }
                currentPage++;

            } catch (Exception e) {
                log.error("分页查询活动失败: page={}", currentPage, e);
                break;
            }
        }

        log.info("活动同步完成: 总处理={}, 成功={}, 失败={}",
                successCount.get() + failCount.get(), successCount.get(), failCount.get());
    }

    /**
     * 同步所有宠物信息（修复版）
     */
    public void syncPetPosts() {
        log.info("========== 开始同步宠物信息数据 ==========");
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        int pageSize = 100;
        int currentPage = 1;

        while (true) {
            try {
                Page<PetPost> page = new Page<>(currentPage, pageSize);
                Page<PetPost> petPage = petPostMapper.selectPage(page, null);

                List<PetPost> petPosts = petPage.getRecords();
                if (petPosts == null || petPosts.isEmpty()) {
                    break;
                }

                for (PetPost petPost : petPosts) {
                    try {
                        syncService.syncPetPost(petPost);
                        successCount.incrementAndGet();
                        log.debug("同步宠物信息成功: id={}", petPost.getId());
                    } catch (Exception e) {
                        log.error("同步宠物信息失败: id={}, error={}", petPost.getId(), e.getMessage(), e);
                        failCount.incrementAndGet();  // ✅ 关键修复：增加失败计数
                    }
                }

                log.info("宠物信息同步进度: 已处理 {} 条, 成功={}, 失败={}",
                        successCount.get() + failCount.get(), successCount.get(), failCount.get());

                if (petPage.getCurrent() >= petPage.getPages()) {
                    break;
                }
                currentPage++;

            } catch (Exception e) {
                log.error("分页查询宠物信息失败: page={}", currentPage, e);
                break;
            }
        }

        log.info("宠物信息同步完成: 总处理={}, 成功={}, 失败={}",
                successCount.get() + failCount.get(), successCount.get(), failCount.get());
    }

    /**
     * 清空索引并重建（修复版）
     */
    @Async
    public void rebuildIndex() {
        log.info("========== 开始重建ES索引 ==========");
        try {
            // 删除索引
            log.info("1. 删除现有索引...");
            syncService.deleteIndex();

            // 重新创建索引
            log.info("2. 创建新索引...");
            syncService.createIndex();

            // 等待索引创建完成
            Thread.sleep(1000);

            // 全量同步
            log.info("3. 开始全量同步数据...");
            syncAll();

        } catch (Exception e) {
            log.error("重建索引失败", e);
        }
    }
}