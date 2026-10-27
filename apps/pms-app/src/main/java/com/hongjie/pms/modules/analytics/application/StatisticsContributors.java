package com.hongjie.pms.modules.analytics.application;

import com.hongjie.pms.modules.analytics.infrastructure.persistence.entity.DailyStatistics;
import org.springframework.context.annotation.*;
import java.time.LocalDateTime;
import java.util.function.*;

@Configuration(proxyBeanMethods = false)
public class StatisticsContributors {
    private StatisticsFacts read(
            StatisticsFactMapper mapper,
            StatisticsSource source,
            LocalDateTime start,
            LocalDateTime end) {
        return mapper.aggregate(source, start, end, start.minusDays(6), start.minusDays(29));
    }

    @Bean
    StatisticsContributor userStatistics(StatisticsFactMapper mapper) {
        return group(
                "users",
                (row, start, end) -> {
                    var f = read(mapper, StatisticsSource.USERS, start, end);
                    row.setNewUserCount(f.getCreated());
                    row.setTotalUserCount(f.getTotal());
                    row.setActiveUserCount(f.getActive());
                    row.setDau(f.getActive());
                    row.setWau(f.getWeekActive());
                    row.setMau(f.getMonthActive());
                });
    }

    @Bean
    StatisticsContributor contentStatistics(StatisticsFactMapper mapper) {
        return group(
                "content",
                (row, start, end) -> {
                    var pet = read(mapper, StatisticsSource.PETS, start, end);
                    row.setNewPetPostCount(pet.getCreated());
                    row.setTotalPetPostCount(pet.getTotal());
                    var activity = read(mapper, StatisticsSource.ACTIVITIES, start, end);
                    row.setNewActivityCount(activity.getCreated());
                    row.setTotalActivityCount(activity.getTotal());
                    var daily = read(mapper, StatisticsSource.DAILY, start, end);
                    row.setNewDailyPostCount(daily.getCreated());
                    row.setTotalDailyPostCount(daily.getTotal());
                    var comment = read(mapper, StatisticsSource.COMMENTS, start, end);
                    row.setNewCommentCount(comment.getCreated());
                    row.setTotalCommentCount(comment.getTotal());
                });
    }

    @Bean
    StatisticsContributor interactionStatistics(StatisticsFactMapper mapper) {
        return group(
                "interactions",
                (row, start, end) -> {
                    var likes = read(mapper, StatisticsSource.LIKES, start, end);
                    row.setNewLikeCount(likes.getCreated());
                    row.setTotalLikeCount(likes.getTotal());
                    var follows = read(mapper, StatisticsSource.FOLLOWS, start, end);
                    row.setNewFollowCount(follows.getCreated());
                    row.setTotalFollowCount(follows.getTotal());
                    var favorites = read(mapper, StatisticsSource.FAVORITES, start, end);
                    row.setNewFavoriteCount(favorites.getCreated());
                    row.setTotalFavoriteCount(favorites.getTotal());
                    row.setNewShareCount(0);
                    row.setTotalShareCount(0);
                });
    }

    @Bean
    StatisticsContributor signupStatistics(StatisticsFactMapper mapper) {
        return group(
                "signups",
                (row, start, end) -> {
                    var f = read(mapper, StatisticsSource.SIGNUPS, start, end);
                    row.setNewSignupCount(f.getCreated());
                    row.setTotalSignupCount(f.getTotal());
                    row.setNewCheckinCount(f.getCheckins());
                    row.setTotalCheckinCount(f.getTotalCheckins());
                });
    }

    @Bean
    StatisticsContributor moderationStatistics(StatisticsFactMapper mapper) {
        return group(
                "moderation",
                (row, start, end) -> {
                    var audit = read(mapper, StatisticsSource.AUDITS, start, end);
                    row.setPendingAuditCount(audit.getPending());
                    row.setApprovedCount(audit.getApproved());
                    row.setRejectedCount(audit.getRejected());
                    var report = read(mapper, StatisticsSource.REPORTS, start, end);
                    row.setNewReportCount(report.getCreated());
                    row.setPendingReportCount(report.getPending());
                    row.setHandledReportCount(report.getHandled());
                });
    }

    @Bean
    StatisticsContributor messageStatistics(StatisticsFactMapper mapper) {
        return group(
                "messages",
                (row, start, end) -> {
                    var f = read(mapper, StatisticsSource.MESSAGES, start, end);
                    row.setNewPrivateMessageCount(f.getCreated());
                    row.setTotalPrivateMessageCount(f.getTotal());
                });
    }

    @FunctionalInterface
    private interface Fill {
        void accept(DailyStatistics row, LocalDateTime start, LocalDateTime end);
    }

    private StatisticsContributor group(String name, Fill fill) {
        return new StatisticsContributor() {
            public String name() {
                return name;
            }

            public void collect(DailyStatistics row, LocalDateTime start, LocalDateTime end) {
                fill.accept(row, start, end);
            }
        };
    }
}
