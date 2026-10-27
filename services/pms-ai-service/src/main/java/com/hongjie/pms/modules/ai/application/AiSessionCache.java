package com.hongjie.pms.modules.ai.application;

import com.hongjie.pms.shared.core.cache.AfterCommitCache;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiSessionCache {
    private final RedisTemplate<String, Object> redis;

    public void clear(String id) {
        redis.delete("ai:memory:session:" + id);
    }

    public void afterCommit(String id) {
        AfterCommitCache.evict(() -> clear(id));
    }
}
