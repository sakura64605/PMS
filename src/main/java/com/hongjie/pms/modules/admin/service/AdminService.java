package com.hongjie.pms.modules.admin.service;

import com.hongjie.pms.modules.petpost.dto.response.PetListResponseDto;

public interface AdminService {
    PetListResponseDto accept(Long id);

    PetListResponseDto reject(Long id);
}
