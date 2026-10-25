package com.hongjie.pms.refactor.statistics;
import com.hongjie.pms.common.cache.DistributedCache;
import com.hongjie.pms.common.utils.RedisScanUtil;
import com.hongjie.pms.modules.activity.mapper.*;
import com.hongjie.pms.modules.audit.mapper.AuditRecordMapper;
import com.hongjie.pms.modules.comment.mapper.CommentMapper;
import com.hongjie.pms.modules.daily.mapper.DailyPostMapper;
import com.hongjie.pms.modules.following.mapper.FollowMapper;
import com.hongjie.pms.modules.like.mapper.LikeRecordMapper;
import com.hongjie.pms.modules.petpost.mapper.*;
import com.hongjie.pms.modules.privateMessage.mapper.PrivateMessageMapper;
import com.hongjie.pms.modules.report.mapper.ReportRecordMapper;
import com.hongjie.pms.modules.statistics.mapper.DailyStatisticsMapper;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.modules.statistics.service.StatisticsService;
import com.hongjie.pms.modules.statistics.dto.StatisticsResponseDto.*;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import java.time.LocalDate;

/** Compatibility facade for the existing controllers and XXL-JOB actions. */
@Service @Primary
public class PmsStatisticsService extends StatisticsService {
    private final StatisticsQueries queries;
    private final StatisticsGeneration generation;
    private final StatisticsCache cache;
    public PmsStatisticsService(DailyStatisticsMapper rows,UserMapper users,PetPostMapper pets,
            ActivityMapper activities,DailyPostMapper daily,CommentMapper comments,LikeRecordMapper likes,
            FollowMapper follows,FavoriteRecordMapper favorites,ActivitySignupMapper signups,
            AuditRecordMapper audits,ReportRecordMapper reports,PrivateMessageMapper messages,
            DistributedCache distributedCache,RedisScanUtil scan,
            StatisticsQueries queries,StatisticsGeneration generation,StatisticsCache cache) {
        super(rows,users,pets,activities,daily,comments,likes,follows,favorites,signups,audits,reports,messages,distributedCache,scan);
        this.queries=queries;this.generation=generation;this.cache=cache;
    }
    @Override public DailyStatisticsDto getDailyStatistics(LocalDate date){return queries.daily(date);}
    @Override public WeeklyStatisticsDto getWeeklyStatistics(LocalDate date){return queries.weekly(date);}
    @Override public MonthlyStatisticsDto getMonthlyStatistics(String month){return queries.monthly(month);}
    @Override public YearlyStatisticsDto getYearlyStatistics(int year){return queries.yearly(year);}
    @Override public RangeStatisticsDto getRangeStatistics(LocalDate start,LocalDate end){return queries.range(start,end);}
    @Override public DailyStatisticsDto getRealtimeStatistics(){return queries.realtime();}
    @Override public void generateDailyStatistics(){generation.daily();}
    @Override public void compensateMissingStatistics(){generation.compensate();}
    @Override public void regenerateStatistics(LocalDate date){generation.regenerate(date);}
    @Override public void regenerateStatisticsRange(LocalDate start,LocalDate end){generation.regenerateRange(start,end);}
    @Override public void clearAllStatisticsCache(){
        if(!UserContext.isAdmin())throw new BusinessException(ErrorCode.FORBIDDEN);
        cache.afterCommit();
    }
}
