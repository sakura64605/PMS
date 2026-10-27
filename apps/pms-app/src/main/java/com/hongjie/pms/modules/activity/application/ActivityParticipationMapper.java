package com.hongjie.pms.modules.activity.application;

import com.hongjie.pms.modules.activity.infrastructure.persistence.entity.Activity;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ActivityParticipationMapper {
    @Select(
            "SELECT id,user_id,title,status,audit_status,deleted,current_people,max_people,start_time,end_time FROM activity WHERE id=#{id} FOR UPDATE")
    Activity lock(@Param("id") Long id);

    @Update(
            "UPDATE activity SET current_people=current_people+1 WHERE id=#{id} AND deleted=0 AND audit_status=1 AND status=0 AND current_people<max_people AND start_time>#{now}")
    int reserve(@Param("id") Long id, @Param("now") java.time.LocalDateTime now);

    @Update(
            "UPDATE activity SET current_people=current_people-1 WHERE id=#{id} AND current_people>0")
    int release(@Param("id") Long id);
}
