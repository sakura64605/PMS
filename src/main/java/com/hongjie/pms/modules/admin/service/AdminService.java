package com.hongjie.pms.modules.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.modules.admin.dto.response.AdminUserSimpleDto;
import com.hongjie.pms.modules.petpost.dto.response.PetListResponseDto;
import com.hongjie.pms.modules.user.dto.UserSimpleDto;

public interface AdminService {
    PetListResponseDto accept(Long id);

    PetListResponseDto reject(Long id, String reason);

    IPage<AdminUserSimpleDto> userList(int pageNum, int pageSize, String keyword, Integer status);

    void disableUser(Long userId);

    void enableUser(Long userId);
}
