package com.hongjie.pms.modules.analytics.application;

import java.util.*;

public final class StatisticsSql {
    private StatisticsSql() {}

    private static String sum(String expression, String alias) {
        return ",COALESCE(SUM(" + expression + "),0) AS " + alias;
    }

    public static String aggregate(Map<String, Object> params) {
        StatisticsSource source = (StatisticsSource) params.get("source");
        String sql = "SELECT COUNT(*) AS total,COALESCE(SUM(create_time>=#{start}),0) AS created";
        switch (source) {
            case USERS ->
                    sql +=
                            sum("last_active_time>=#{start} AND last_active_time<#{end}", "active")
                                    + sum(
                                            "last_active_time>=#{week} AND last_active_time<#{end}",
                                            "week_active")
                                    + sum(
                                            "last_active_time>=#{month} AND last_active_time<#{end}",
                                            "month_active");
            case SIGNUPS ->
                    sql +=
                            sum("check_in_time>=#{start} AND check_in_time<#{end}", "checkins")
                                    + sum("check_in_time<#{end}", "total_checkins");
            case AUDITS ->
                    sql +=
                            sum("audit_status=0 OR audit_time>=#{end}", "pending")
                                    + sum(
                                            "audit_status=1 AND COALESCE(audit_time,create_time)>=#{start} AND COALESCE(audit_time,create_time)<#{end}",
                                            "approved")
                                    + sum(
                                            "audit_status=2 AND COALESCE(audit_time,create_time)>=#{start} AND COALESCE(audit_time,create_time)<#{end}",
                                            "rejected");
            case REPORTS ->
                    sql +=
                            sum("status=0 OR handle_time>=#{end}", "pending")
                                    + sum(
                                            "status=1 AND COALESCE(handle_time,create_time)<#{end}",
                                            "handled");
            default -> {}
        }
        return sql + " FROM `" + source.table + "` WHERE create_time<#{end}";
    }

    public static String earliest() {
        return "SELECT DATE(MIN(first_time)) FROM ("
                + Arrays.stream(StatisticsSource.values())
                        .map(
                                source ->
                                        "SELECT MIN(create_time) first_time FROM `"
                                                + source.table
                                                + "`")
                        .collect(java.util.stream.Collectors.joining(" UNION ALL "))
                + ") sources";
    }
}
