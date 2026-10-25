package com.hongjie.pms.modules.analytics.application;
import com.hongjie.pms.platform.utils.RedisScanUtil;
import com.hongjie.pms.shared.core.cache.AfterCommitCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class StatisticsCache {
    private final RedisScanUtil scan;
    public void afterCommit(){AfterCommitCache.evict(()->scan.scanAndDelete("statistics:*"));}
}
