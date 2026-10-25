package com.hongjie.pms.refactor.daily;
import java.util.List;
public interface DailyRecallSource {
    String name();
    List<Long> recall(Long actor,int limit);
}
