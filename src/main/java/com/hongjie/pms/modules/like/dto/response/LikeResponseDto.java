package com.hongjie.pms.modules.like.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LikeResponseDto {
    private Boolean isLiked;    // 当前是否点赞
    private Integer likeCount;  // 最新点赞数
}