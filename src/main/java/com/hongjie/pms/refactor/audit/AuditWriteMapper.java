package com.hongjie.pms.refactor.audit;

import com.hongjie.pms.modules.audit.entity.AuditRecord;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 固定 SQL 的当前读，避免可重复读快照导致“查不到刚提交的待审记录”。 */
@Mapper
public interface AuditWriteMapper {
    @Select("SELECT id FROM pet_post WHERE id=#{id} AND type=#{type} AND status NOT IN (-1,2) FOR UPDATE")
    Long lockPet(@Param("id") Long id, @Param("type") int type);
    @Select("SELECT id FROM activity WHERE id=#{id} AND deleted=0 AND status<>2 FOR UPDATE")
    Long lockActivity(@Param("id") Long id);
    @Select("SELECT id FROM daily_post WHERE id=#{id} AND status=1 FOR UPDATE")
    Long lockDaily(@Param("id") Long id);
    @Select("SELECT * FROM audit_record WHERE target_type=#{type} AND target_id=#{id} AND audit_status=0 FOR UPDATE")
    List<AuditRecord> pending(@Param("type") String type, @Param("id") Long id);
}
