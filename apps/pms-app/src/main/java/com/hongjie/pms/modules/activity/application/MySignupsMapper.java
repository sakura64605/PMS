package com.hongjie.pms.modules.activity.application;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.hongjie.pms.modules.activity.infrastructure.persistence.entity.ActivitySignup;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MySignupsMapper {
    String JOIN = " FROM activity_signup s JOIN activity a ON a.id=s.activity_id LEFT JOIN user u ON u.id=a.user_id ";
    @Select("SELECT s.*" + JOIN + "${ew.customSqlSegment}")
    IPage<ActivitySignup> page(IPage<ActivitySignup> page, @Param(Constants.WRAPPER) Wrapper<ActivitySignup> query);

    @Select("""
        SELECT COUNT(*) AS total,
          COALESCE(SUM(s.status=1 AND a.deleted=0 AND a.audit_status=1 AND a.status IN (0,1) AND a.end_time>#{now}),0) AS pending,
          COALESCE(SUM(s.status=3),0) AS checkedIn,
          COALESCE(SUM(a.status=2 OR a.end_time<=#{now}),0) AS finished
        FROM activity_signup s JOIN activity a ON a.id=s.activity_id
        WHERE s.user_id=#{actor} AND s.status IN (1,3,4)
        """)
    java.util.Map<String, Object> summary(@Param("actor") Long actor, @Param("now") java.time.LocalDateTime now);
}
