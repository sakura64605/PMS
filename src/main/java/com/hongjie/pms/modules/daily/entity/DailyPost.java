package com.hongjie.pms.modules.daily.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Data
@TableName(value = "daily_post", autoResultMap = true)
public class DailyPost {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String content;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> images;

    private String videoUrl;
    private Long topicId;
    private String location;

    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private Integer shareCount;

    private Integer status;
    private Integer auditStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

}