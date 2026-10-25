package com.hongjie.pms.refactor.user;
import com.hongjie.pms.common.cache.DistributedCache;
import com.hongjie.pms.common.cache.toolkit.CacheUtil;
import com.hongjie.pms.refactor.core.cache.AfterCommitCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class UserCache {
    private final DistributedCache cache;
    public void afterCommit(Long id) { AfterCommitCache.evict(() -> cache.delete(CacheUtil.buildKey("user", id.toString()))); }
}
