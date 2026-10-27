package com.hongjie.pms.modules.community.comment.api.dto.request;

import lombok.Data;

@Data
public class CommentCreateRequest {

    private Long commentId;
    private String targetType;
    private Long targetId;
    private String content;
    private Long parentId;
    private Long replyTo;
}
