package com.hongjie.pms.modules.governance.report.application;

import com.hongjie.pms.modules.identity.user.infrastructure.persistence.entity.User;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ReportWriteMapper {
    @Select("SELECT id FROM pet_post WHERE id=#{id} AND status != -1 FOR UPDATE")
    Long lockPet(Long id);

    @Select("SELECT id FROM activity WHERE id=#{id} AND deleted=0 FOR UPDATE")
    Long lockActivity(Long id);

    @Select("SELECT id FROM comment WHERE id=#{id} FOR UPDATE")
    Long lockComment(Long id);

    @Select("SELECT id, mute_end_time FROM user WHERE id=#{id} FOR UPDATE")
    User lockUser(Long id);

    @Select({
        "<script>",
        "SELECT id FROM report_record WHERE reporter_id=#{actor} AND target_id=#{id} AND status=0 AND target_type IN",
        "<foreach collection='types' item='type' open='(' separator=',' close=')'>#{type}</foreach>",
        "FOR UPDATE",
        "</script>"
    })
    List<Long> pending(
            @Param("actor") Long actor, @Param("id") Long id, @Param("types") List<String> types);
}
