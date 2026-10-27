package com.hongjie.pms.modules.community.like.api.dto.request;

import lombok.Data;

@Data
public class LikeRequest {

    private Long targetId;
    private String targetType;
}
