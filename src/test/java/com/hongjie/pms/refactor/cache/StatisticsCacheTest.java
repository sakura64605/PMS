package com.hongjie.pms.refactor.cache;

import com.hongjie.pms.modules.statistics.service.StatisticsService;
import com.hongjie.pms.modules.statistics.mapper.DailyStatisticsMapper;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.modules.petpost.mapper.*;
import com.hongjie.pms.modules.activity.mapper.*;
import com.hongjie.pms.modules.daily.mapper.DailyPostMapper;
import com.hongjie.pms.modules.comment.mapper.CommentMapper;
import com.hongjie.pms.modules.like.mapper.LikeRecordMapper;
import com.hongjie.pms.modules.following.mapper.FollowMapper;
import com.hongjie.pms.modules.audit.mapper.AuditRecordMapper;
import com.hongjie.pms.modules.report.mapper.ReportRecordMapper;
import com.hongjie.pms.modules.privateMessage.mapper.PrivateMessageMapper;
import com.hongjie.pms.common.cache.DistributedCache;
import com.hongjie.pms.common.utils.RedisScanUtil;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class StatisticsCacheTest {
    DistributedCache cache=mock(DistributedCache.class);
    RedisScanUtil scan=mock(RedisScanUtil.class);
    LikeRecordMapper likes=mock(LikeRecordMapper.class);
    StatisticsService service=new StatisticsService(mock(DailyStatisticsMapper.class), mock(UserMapper.class),
            mock(PetPostMapper.class),mock(ActivityMapper.class),mock(DailyPostMapper.class),
            mock(CommentMapper.class),likes,mock(FollowMapper.class),mock(FavoriteRecordMapper.class),
            mock(ActivitySignupMapper.class),mock(AuditRecordMapper.class),mock(ReportRecordMapper.class),
            mock(PrivateMessageMapper.class),cache,scan);
    @Test void realtimeCardsReflectTheNextCompletedWrite() {
        when(likes.selectCount(any())).thenReturn(1L,2L);
        assertThat(service.getRealtimeStatistics().getNewLikeCount()).isEqualTo(1);
        assertThat(service.getRealtimeStatistics().getNewLikeCount()).isEqualTo(2);
        verifyNoInteractions(cache);
    }
    @Test void historicalRebuildEvictionWaitsForCommit() {
        new TransactionTemplate(new CacheCommitTest.Transactions()).executeWithoutResult(status -> {
            service.clearAllStatisticsCache();
            verifyNoInteractions(scan);
        });
        verify(scan).scanAndDelete("statistics:*");
    }
}
