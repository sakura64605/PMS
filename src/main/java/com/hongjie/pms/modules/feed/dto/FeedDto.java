// FeedDto.java
package com.hongjie.pms.modules.feed.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class FeedDto {
    
    private Long id;
    private Long postId;
    private String postType;      // pet/activity
    private String title;
    private String content;
    private String coverImage;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    
    // 发布者信息
    private Long posterId;
    private String posterName;
    private String posterAvatar;
    
    // 互动状态
    private Boolean isLiked;
    private Boolean isFavorite;
    
    // 时间
    private LocalDateTime createTime;
}