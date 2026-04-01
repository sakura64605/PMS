package com.hongjie.pms.modules.admin.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.modules.admin.service.AdminService;
import com.hongjie.pms.modules.petpost.dto.response.PetListResponseDto;
import com.hongjie.pms.modules.petpost.entity.PetPost;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final PetPostMapper petPostMapper;

    @Override
    public PetListResponseDto accept(Long id) {
        LambdaQueryWrapper<PetPost> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PetPost::getId, id);
        PetPost petPost = petPostMapper.selectOne(queryWrapper);
        if (petPost == null){
            throw new RuntimeException("未找到该宠物信息");
        }
        petPost.setStatus(1);
        petPostMapper.updateById(petPost);
        return null;
    }

    @Override
    public PetListResponseDto reject(Long id) {
        LambdaQueryWrapper<PetPost> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PetPost::getId, id);
        PetPost petPost = petPostMapper.selectOne(queryWrapper);
        if (petPost == null){
            throw new RuntimeException("未找到该宠物信息");
        }
        petPost.setStatus(4);
        petPostMapper.updateById(petPost);
        return null;
    }
}
