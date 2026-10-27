package com.hongjie.pms.modules.content.daily.application;

import java.util.List;

public interface DailyRecallSource {
    String name();

    List<Long> recall(Long actor, int limit);
}
