package com.hongjie.pms.refactor.statistics;
import lombok.Data;
@Data
public class StatisticsFacts {
    private int created,total,active,weekActive,monthActive,checkins,totalCheckins,pending,approved,rejected,handled;
}
