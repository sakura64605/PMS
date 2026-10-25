package com.hongjie.pms.refactor.statistics;

import com.hongjie.pms.modules.statistics.dto.StatisticsResponseDto.*;
import com.hongjie.pms.modules.statistics.entity.DailyStatistics;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.ToIntFunction;

@Component
public class StatisticsAssembler {
    static int n(Integer value) { return value == null ? 0 : value; }
    static int posts(DailyStatistics row) {
        return n(row.getNewPetPostCount()) + n(row.getNewActivityCount()) + n(row.getNewDailyPostCount());
    }
    int sum(List<DailyStatistics> rows, ToIntFunction<DailyStatistics> field) {
        return rows.stream().mapToInt(field).sum();
    }
    int average(List<DailyStatistics> rows) {
        return rows.isEmpty() ? 0 : sum(rows, r -> n(r.getDau())) / rows.size();
    }
    int growth(int current, int previous) {
        return previous == 0 ? (current == 0 ? 0 : 100) : (int)Math.round((current - previous) * 100.0 / previous);
    }
    public TrendDataDto trend(List<DailyStatistics> rows) {
        return TrendDataDto.builder()
                .dates(rows.stream().map(r -> r.getStatDate().toString()).toList())
                .dauList(rows.stream().map(r -> n(r.getDau())).toList())
                .newUserList(rows.stream().map(r -> n(r.getNewUserCount())).toList())
                .newPostList(rows.stream().map(StatisticsAssembler::posts).toList())
                .newCommentList(rows.stream().map(r -> n(r.getNewCommentCount())).toList())
                .newLikeList(rows.stream().map(r -> n(r.getNewLikeCount())).toList()).build();
    }
    public DailyStatisticsDto daily(DailyStatistics r, List<DailyStatistics> trend) {
        return DailyStatisticsDto.builder().statDate(r.getStatDate())
                .newUserCount(n(r.getNewUserCount())).activeUserCount(n(r.getActiveUserCount())).dau(n(r.getDau()))
                .newPetPostCount(n(r.getNewPetPostCount())).newActivityCount(n(r.getNewActivityCount()))
                .newDailyPostCount(n(r.getNewDailyPostCount())).newCommentCount(n(r.getNewCommentCount()))
                .newLikeCount(n(r.getNewLikeCount())).newFollowCount(n(r.getNewFollowCount()))
                .newSignupCount(n(r.getNewSignupCount())).newReportCount(n(r.getNewReportCount()))
                .pendingAuditCount(n(r.getPendingAuditCount())).trendData(trend(trend)).build();
    }
    public RangeStatisticsDto range(List<DailyStatistics> rows) {
        return RangeStatisticsDto.builder().startDate(rows.get(0).getStatDate())
                .endDate(rows.get(rows.size()-1).getStatDate())
                .totalNewUsers(sum(rows, r -> n(r.getNewUserCount()))).avgActiveUsers(average(rows))
                .maxDau(rows.stream().mapToInt(r -> n(r.getDau())).max().orElse(0))
                .minDau(rows.stream().mapToInt(r -> n(r.getDau())).min().orElse(0))
                .totalNewPosts(sum(rows, StatisticsAssembler::posts))
                .totalNewComments(sum(rows, r -> n(r.getNewCommentCount())))
                .totalNewLikes(sum(rows, r -> n(r.getNewLikeCount()))).trendData(trend(rows)).build();
    }
}
