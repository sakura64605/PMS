package com.hongjie.pms.modules.like.dto.request;

import lombok.Data;

@Data
public class LikeRequest {

    private Long targetId;
    private String targetType;

}
