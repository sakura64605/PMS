package com.hongjie.pms.modules.community.comment.api.dto.response;

import com.hongjie.pms.modules.identity.user.api.dto.UserSimpleDto;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CommentRespDto {

    private Long id;
    private UserSimpleDto user;
    private String content;
    private Integer likeCount;
    private Boolean isLiked;      // 当前用户是否点赞
    private LocalDateTime createTime;
    private UserSimpleDto replyTo;
    private Long parentId;
    private List<CommentRespDto> replies;  // 子评论列表

}
