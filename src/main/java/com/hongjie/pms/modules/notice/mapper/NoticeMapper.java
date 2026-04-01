package com.hongjie.pms.modules.notice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongjie.pms.modules.notice.entity.Notice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NoticeMapper extends BaseMapper<Notice> {
    
    @Select("SELECT COUNT(*) FROM notice WHERE status = 1 AND publish_time <= NOW() " +
            "AND (expire_time IS NULL OR expire_time > NOW())")
    int countPublished();
    
    @Select("SELECT * FROM notice WHERE status = 1 AND publish_time <= NOW() " +
            "AND (expire_time IS NULL OR expire_time > NOW()) " +
            "ORDER BY is_top DESC, priority DESC, publish_time DESC")
    List<Notice> getPublishedNotices();
}