package com.hongjie.pms.modules.admin.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.admin.dto.response.AdminUserSimpleDto;
import com.hongjie.pms.modules.admin.service.AdminService;
import com.hongjie.pms.modules.message.service.MessageService;
import com.hongjie.pms.modules.petpost.dto.response.PetListResponseDto;
import com.hongjie.pms.modules.petpost.entity.PetPost;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.modules.user.dto.UserSimpleDto;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final PetPostMapper petPostMapper;
    private final UserMapper userMapper;
    private final MessageService messageService;

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
        messageService.sendAuditPassNotification(
                petPost.getUserId(),
                petPost.getTitle(),
                petPost.getId(),
                "pet_post"
        );
        return null;
    }

    @Override
    public PetListResponseDto reject(Long id, String reason) {
        LambdaQueryWrapper<PetPost> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PetPost::getId, id);
        PetPost petPost = petPostMapper.selectOne(queryWrapper);
        if (petPost == null){
            throw new RuntimeException("未找到该宠物信息");
        }
        petPost.setStatus(4);
        petPostMapper.updateById(petPost);
        messageService.sendAuditRejectNotification(
                petPost.getUserId(),
                petPost.getTitle(),
                petPost.getId(),
                "pet_post",
                reason
        );
        return null;
    }

    @Override
    public IPage<AdminUserSimpleDto> userList(int pageNum, int pageSize, String keyword, Integer status) {
        // 1. 权限校验
        if (!UserContext.isAdmin()) {
            throw new BusinessException(403, "无权限查看");
        }

        // 2. 构建查询条件
        Page<User> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();

        // 状态筛选
        if (status != null) {
            queryWrapper.eq(User::getStatus, status);
        }

        // 关键词搜索（用户名/昵称/手机号）
        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(w -> w
                    .like(User::getUserName, keyword)
                    .or()
                    .like(User::getNickName, keyword)
                    .or()
                    .like(User::getPhone, keyword)
            );
        }

        // 排序：按创建时间倒序
        queryWrapper.orderByDesc(User::getCreateTime);

        // 3. 执行查询
        IPage<User> userPage = userMapper.selectPage(page, queryWrapper);

        // 4. 转换为 DTO
        List<AdminUserSimpleDto> records = userPage.getRecords().stream()
                .map(user -> AdminUserSimpleDto.builder()
                        .userId(user.getId())
                        .username(user.getUserName())
                        .nickname(user.getNickName())
                        .avatar(user.getAvatar())
                        .isDisable(user.getStatus() == 0)
                        .build())
                .collect(Collectors.toList());

        // 5. 返回分页结果
        Page<AdminUserSimpleDto> resultPage = new Page<>(pageNum, pageSize, userPage.getTotal());
        resultPage.setRecords(records);
        return resultPage;
    }

    @Override
    public void disableUser(Long userId) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getId, userId);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            throw new BusinessException(400, "用户不存在");
        }
        user.setStatus(0);
        userMapper.updateById(user);
        log.info("禁用用户成功，userId={}", userId);
    }

    @Override
    public void enableUser(Long userId) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getId, userId);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            throw new BusinessException(400, "用户不存在");
        }
        user.setStatus(1);
        userMapper.updateById(user);
        log.info("启用用户成功，userId={}", userId);
    }
}
