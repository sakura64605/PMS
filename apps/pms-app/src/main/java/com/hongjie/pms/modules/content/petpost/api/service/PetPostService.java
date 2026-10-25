package com.hongjie.pms.modules.content.petpost.api.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.modules.content.petpost.api.dto.PetDetailDto;
import com.hongjie.pms.modules.content.petpost.api.dto.request.PetPostRequestDto;
import com.hongjie.pms.modules.content.petpost.api.dto.request.PetQueryRequestDto;
import com.hongjie.pms.modules.identity.user.api.dto.response.AvatarUploadResponse;
import com.hongjie.pms.modules.content.petpost.api.dto.response.FavoriteResponseDto;
import com.hongjie.pms.modules.content.petpost.api.dto.response.PetListResponseDto;
import org.springframework.web.multipart.MultipartFile;

public interface PetPostService {
    PetListResponseDto post(PetPostRequestDto request);

    IPage<PetListResponseDto> list(PetQueryRequestDto request);

    PetDetailDto detail(Long id, Long userId);

    PetListResponseDto update(PetPostRequestDto request);

    AvatarUploadResponse uploadImage(MultipartFile file);

    void delete(Long id);

    void offline(Long id);

    void recover(Long id);

    void deleteReally(Long id);

    FavoriteResponseDto favorite(Long id);

    IPage<PetListResponseDto> favoriteList(Integer pageNum, Integer pageSize);

    void complete(Long id);
}
