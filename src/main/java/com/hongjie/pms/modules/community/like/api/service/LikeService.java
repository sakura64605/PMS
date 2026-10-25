package com.hongjie.pms.modules.community.like.api.service;

import com.hongjie.pms.modules.community.like.api.dto.request.LikeRequest;
import com.hongjie.pms.modules.community.like.api.dto.response.LikeResponseDto;

public interface LikeService {
    LikeResponseDto like(LikeRequest request);
}
