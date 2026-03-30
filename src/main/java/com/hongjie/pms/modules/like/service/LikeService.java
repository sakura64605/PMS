package com.hongjie.pms.modules.like.service;

import com.hongjie.pms.modules.like.dto.request.LikeRequest;
import com.hongjie.pms.modules.like.dto.response.LikeResponseDto;

public interface LikeService {
    LikeResponseDto like(LikeRequest request);
}
