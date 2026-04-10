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

    private final UserMapper userMapper;
    private final PetPostMapper petPostMapper;

    private final ActivityMapper activityMapper;

    // 统一布隆过滤器
    private BloomFilter<CharSequence> bloomFilter;

    // 预期总数据量（用户+宠物+活动）
    private static final long EXPECTED_INSERTIONS = 5000000;  // 500万

    // 误判率
    private static final double FPP = 0.01;

    @PostConstruct
    public void init() {
        log.info("初始化统一布隆过滤器...");
        long start = System.currentTimeMillis();

        // 创建布隆过滤器
        bloomFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                EXPECTED_INSERTIONS,
                FPP
        );

        int totalLoaded = 0;

        // 加载所有用户
        totalLoaded += loadUserIds();

        long end = System.currentTimeMillis();
        log.info("布隆过滤器初始化完成，加载 {} 条数据，耗时 {}ms", totalLoaded, end - start);
    }

    private int loadUserIds() {
        int count = 0;
        int pageSize = 10000;
        int current = 1;

        while (true) {
            Page<User> page = new Page<>(current, pageSize);

            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                    .eq(User::getStatus, 1)
                    .select(User::getId);

            Page<User> result = userMapper.selectPage(page, wrapper);

            List<Long> ids = result.getRecords().stream()
                    .map(User::getId)
                    .collect(Collectors.toList());

            if (ids.isEmpty()) {
                break;
            }

            for (Long id : ids) {
                bloomFilter.put("user:" + id);
                count++;
            }

            if (current >= result.getPages()) {
                break;
            }
            current++;
        }

        log.info("加载用户: {} 条", count);
        return count;
    }

    /**
     * 检查ID是否存在
     *
     * @param type 类型（user/pet/activity）
     * @param id ID
     */
    public boolean mightExist(String type, Long id) {
        if (id == null || id <= 0) {
            return false;
        }
        return bloomFilter.mightContain(type + ":" + id);
    }

    /**
     * 添加ID到布隆过滤器
     */
    public void add(String type, Long id) {
        if (id != null && id > 0) {
            bloomFilter.put(type + ":" + id);
        }
    }

    /**
     * 批量添加
     */
    public void batchAdd(String type, List<Long> ids) {
        for (Long id : ids) {
            add(type, id);
        }
    }
}