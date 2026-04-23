package com.hongjie.pms.modules.daily.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("topic")
public class Topic {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;           // #猫咪日常#
    private String description;
    private Integer postCount;     // 帖子数
    private Integer viewCount;     // 浏览量
    private Double hotScore;       // 热度分
    private Integer status;        // 1-正常 0-删除

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}