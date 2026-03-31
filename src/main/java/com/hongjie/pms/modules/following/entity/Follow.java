package com.hongjie.pms.modules.following.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("follow")
public class Follow {

    private Long id;

    private Long followerId;

    private Long followingId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

}
