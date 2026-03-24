package com.hongjie.pms.modules.petpost.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.modules.petpost.dto.PetDetailDto;
import com.hongjie.pms.modules.petpost.dto.request.PetPostRequestDto;
import com.hongjie.pms.modules.petpost.dto.request.PetQueryRequestDto;
import com.hongjie.pms.modules.user.dto.response.AvatarUploadResponse;
import com.hongjie.pms.modules.petpost.dto.response.FavoriteResponseDto;
import com.hongjie.pms.modules.petpost.dto.response.LikeResponseDto;
import com.hongjie.pms.modules.petpost.dto.response.PetListResponseDto;
import org.springframework.web.multipart.MultipartFile;

public interface PetPostService {
    PetListResponseDto post(PetPostRequestDto request);

    IPage<PetListResponseDto> list(PetQueryRequestDto request);

    PetDetailDto detail(Long id, Long userId);

    PetListResponseDto update(PetPostRequestDto request);

    AvatarUploadResponse uploadImage(MultipartFile file);

    PetListResponseDto pend(Long id, Integer status);

    void delete(Long id);

    void offline(Long id);

    void recover(Long id);

    void deleteReally(Long id);

    LikeResponseDto like(Long id);

    FavoriteResponseDto favorite(Long id);

    IPage<PetListResponseDto> favoriteList(Integer pageNum, Integer pageSize);
}
