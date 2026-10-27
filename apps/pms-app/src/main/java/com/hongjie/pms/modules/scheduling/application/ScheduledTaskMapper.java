package com.hongjie.pms.modules.scheduling.application;

import com.hongjie.pms.modules.scheduling.infrastructure.persistence.entity.DelayTask;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ScheduledTaskMapper {
    @Select(
            "SELECT id FROM delay_task WHERE status=0 AND execute_time<=#{now} AND id>#{after} ORDER BY id LIMIT #{size}")
    List<Long> due(
            @Param("now") LocalDateTime now, @Param("after") long after, @Param("size") int size);

    @Select("SELECT * FROM delay_task WHERE id=#{id}")
    DelayTask find(Long id);

    @Select(
            "SELECT * FROM delay_task WHERE id=#{id} AND status=0 AND execute_time<=#{now} FOR UPDATE")
    DelayTask lockDue(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Select("SELECT id FROM activity WHERE id=#{id} FOR UPDATE")
    Long lockActivity(Long id);

    @Select("SELECT id FROM user WHERE id=#{id} FOR UPDATE")
    Long lockUser(Long id);

    @Select("SELECT id FROM notice WHERE id=#{id} FOR UPDATE")
    Long lockNotice(Long id);

    @Update("UPDATE delay_task SET status=1 WHERE id=#{id} AND status=0")
    int complete(Long id);

    @Select(
            "SELECT MAX(execute_time) FROM delay_task WHERE task_type='BANNED_PUNISHMENT_END' AND business_id=#{id} AND status=0")
    LocalDateTime latestBanExpiry(Long id);

    @Select(
            "SELECT COUNT(*) FROM delay_task WHERE task_type='ACTIVITY_STATISTICS' AND business_id=#{id} AND status=1")
    int statisticsAlreadyDone(Long id);
}
