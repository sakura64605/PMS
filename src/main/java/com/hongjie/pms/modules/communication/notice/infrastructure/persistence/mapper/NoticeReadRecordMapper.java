package com.hongjie.pms.modules.communication.notice.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongjie.pms.modules.communication.notice.infrastructure.persistence.entity.NoticeReadRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NoticeReadRecordMapper extends BaseMapper<NoticeReadRecord> {
    
    @Select("SELECT COUNT(*) > 0 FROM notice_read_record WHERE notice_id = #{noticeId} AND user_id = #{userId}")
    boolean exists(Long noticeId, Long userId);
    
    @Select("SELECT COUNT(*) FROM notice_read_record WHERE notice_id = #{noticeId}")
    int countReadByNoticeId(Long noticeId);
}