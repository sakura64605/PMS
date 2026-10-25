package com.hongjie.pms.refactor.statistics;
import com.hongjie.pms.common.utils.RedisScanUtil;
import com.hongjie.pms.refactor.core.cache.AfterCommitCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class StatisticsCache {
    private final RedisScanUtil scan;
    public void afterCommit(){AfterCommitCache.evict(()->scan.scanAndDelete("statistics:*"));}
}
