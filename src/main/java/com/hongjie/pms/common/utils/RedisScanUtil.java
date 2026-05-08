package com.hongjie.pms.common.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Redis SCAN 工具类
 * 替代 keys() 命令，避免在生产环境中阻塞 Redis
 *
 * @author Hongjie
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisScanUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 使用 SCAN 命令安全扫描匹配的 key
     *
     * @param pattern 匹配模式，如 "notice:*"
     * @return 匹配的 key 集合
     */
    public Set<String> scanKeys(String pattern) {
        return scanKeys(pattern, 1000L);
    }

    /**
     * 使用 SCAN 命令安全扫描匹配的 key
     * 通过 RedisCallback 执行 SCAN，兼容 Spring Data Redis 3.x
     *
     * @param pattern   匹配模式，如 "notice:*"
     * @param batchSize 每次 SCAN 的批次大小，默认 1000
     * @return 匹配的 key 集合
     */
    public Set<String> scanKeys(String pattern, long batchSize) {
        Set<String> keys = new HashSet<>();
        try {
            ScanOptions options = ScanOptions.scanOptions()
                    .match(pattern)
                    .count(batchSize)
                    .build();

            redisTemplate.execute((RedisCallback<Void>) connection -> {
                try (var cursor = connection.keyCommands().scan(options)) {
                    cursor.forEachRemaining(key -> keys.add(new String((byte[]) key)));
                }
                return null;
            });
        } catch (Exception e) {
            log.error("SCAN扫描key失败, pattern={}", pattern, e);
        }
        return keys;
    }

    /**
     * 使用 SCAN 扫描并删除匹配的 key
     *
     * @param pattern 匹配模式，如 "notice:*"
     * @return 删除的 key 数量
     */
    public long scanAndDelete(String pattern) {
        return scanAndDelete(pattern, 1000L);
    }

    /**
     * 使用 SCAN 扫描并删除匹配的 key
     *
     * @param pattern   匹配模式
     * @param batchSize 每次 SCAN 的批次大小
     * @return 删除的 key 数量
     */
    public long scanAndDelete(String pattern, long batchSize) {
        Set<String> keys = scanKeys(pattern, batchSize);
        if (keys.isEmpty()) {
            return 0;
        }
        Long deleted = redisTemplate.delete(keys);
        log.info("SCAN删除缓存: pattern={}, 共{}个key", pattern, deleted);
        return deleted != null ? deleted : 0;
    }
}
