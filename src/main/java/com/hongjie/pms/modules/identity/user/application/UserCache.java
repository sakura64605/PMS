package com.hongjie.pms.modules.identity.user.application;
import com.hongjie.pms.platform.cache.DistributedCache;
import com.hongjie.pms.platform.cache.toolkit.CacheUtil;
import com.hongjie.pms.shared.core.cache.AfterCommitCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class UserCache {
    private final DistributedCache cache;
    public void afterCommit(Long id) { AfterCommitCache.evict(() -> cache.delete(CacheUtil.buildKey("user", id.toString()))); }
}
