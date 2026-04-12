package com.hongjie.pms.common.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.hongjie.pms.modules.activity.entity.Activity;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.petpost.entity.PetPost;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 统一布隆过滤器
 *
 * 一个过滤器管理所有实体，通过前缀区分
 * key格式: user:123, pet:456, activity:789
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BloomFilterService {

    @Autowired
    private UserMapper userMapper;

    private BloomFilter<CharSequence> bloomFilter;

    @PostConstruct
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        log.info("初始化用户布隆过滤器...");

        bloomFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                1000000,  // 预期100万用户
                0.01
        );

        // 只加载用户ID
        int count = loadUserIds();

        log.info("布隆过滤器初始化完成，加载 {} 条用户数据", count);
    }

    private int loadUserIds() {
        int count = 0;
        long pageSize = 10000;
        long current = 1;

        while (true) {
            Page<User> page = new Page<>(current, pageSize);
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                    .select(User::getId);

            Page<User> result = userMapper.selectPage(page, wrapper);

            if (result.getRecords().isEmpty()) {
                break;
            }

            for (User user : result.getRecords()) {
                bloomFilter.put("user:" + user.getId());
                count++;
            }

            if (current >= result.getPages()) {
                break;
            }
            current++;
        }

        return count;
    }

    /**
     * 只检查用户是否存在
     */
    public boolean userExists(Long userId) {
        if (userId == null || userId <= 0) {
            return false;
        }
        return bloomFilter.mightContain("user:" + userId);
    }

    /**
     * 新增用户时更新
     */
    public void addUser(Long userId) {
        if (userId != null && userId > 0) {
            bloomFilter.put("user:" + userId);
        }
    }
}