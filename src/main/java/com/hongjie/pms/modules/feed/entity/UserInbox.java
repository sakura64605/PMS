package com.hongjie.pms.modules.feed.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_inbox")
public class UserInbox {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long postId;

    private String postType;  // 存枚举的 code: "pet" / "activity"

    private Long posterId;
    private String posterName;
    private String posterAvatar;
    private String title;
    private String coverImage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private Integer isRead;
    private LocalDateTime readTime;
}