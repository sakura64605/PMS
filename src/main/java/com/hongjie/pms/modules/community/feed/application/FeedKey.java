package com.hongjie.pms.modules.community.feed.application;

import lombok.Data;
import java.time.LocalDateTime;

/** Page identity uses both type and ID: pet and activity IDs can overlap. */
@Data
public class FeedKey {
    private String postType;
    private Long postId;
    private Long posterId;
    private LocalDateTime createTime;
}
