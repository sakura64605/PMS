package com.hongjie.pms.modules.notice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("notice_read_record")
public class NoticeReadRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long noticeId;
    private Long userId;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime readTime;
}