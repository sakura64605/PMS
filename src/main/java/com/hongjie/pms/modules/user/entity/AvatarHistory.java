package com.hongjie.pms.modules.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("avatar_history")
public class AvatarHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String avatarUrl;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}