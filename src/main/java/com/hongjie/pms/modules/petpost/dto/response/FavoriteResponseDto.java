package com.hongjie.pms.modules.petpost.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FavoriteResponseDto {
    private Boolean isFavorited;    // 当前是否收藏
}