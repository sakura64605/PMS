package com.hongjie.pms.modules.admin.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.utils.PasswordUtils;
import com.hongjie.pms.modules.admin.dto.response.AdminUserSimpleDto;
import com.hongjie.pms.modules.admin.dto.response.BatchOperationResponse;
import com.hongjie.pms.modules.admin.service.AdminService;
import com.hongjie.pms.modules.message.service.MessageService;
import com.hongjie.pms.modules.petpost.entity.PetPost;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
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
    public void accept(Long id) {
        PetPost petPost = petPostMapper.selectById(id);
        if (petPost == null) {
            throw new BusinessException(ErrorCode.PET_NOT_FOUND);
        }
        petPost.setStatus(1);
        petPostMapper.updateById(petPost);
        messageService.sendAuditPassNotification(
                petPost.getUserId(),
                petPost.getTitle(),
                petPost.getId(),
                "pet_post"
        );
    }

    @Override
    public void reject(Long id, String reason) {
        PetPost petPost = petPostMapper.selectById(id);
        if (petPost == null) {
            throw new BusinessException(ErrorCode.PET_NOT_FOUND);
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
    }

    @Override
    public IPage<AdminUserSimpleDto> userList(int pageNum, int pageSize, String keyword, Integer status) {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Page<User> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();

        if (status != null) {
            queryWrapper.eq(User::getStatus, status);
        }

        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(w -> w
                    .like(User::getUserName, keyword)
                    .or()
                    .like(User::getNickName, keyword)
                    .or()
                    .like(User::getPhone, keyword)
            );
        }

        queryWrapper.orderByDesc(User::getCreateTime);

        IPage<User> userPage = userMapper.selectPage(page, queryWrapper);

        List<AdminUserSimpleDto> records = userPage.getRecords().stream()
                .map(user -> AdminUserSimpleDto.builder()
                        .userId(user.getId())
                        .username(user.getUserName())
                        .nickname(user.getNickName())
                        .avatar(user.getAvatar())
                        .isDisable(user.getStatus() == 0)
                        .build())
                .collect(Collectors.toList());

        Page<AdminUserSimpleDto> resultPage = new Page<>(pageNum, pageSize, userPage.getTotal());
        resultPage.setRecords(records);
        return resultPage;
    }

    /**
     * 批量禁用用户
     */
    @Override
    @Transactional
    public BatchOperationResponse batchDisableUsers(List<Long> userIds) {
        int successCount = 0;
        int failCount = 0;
        List<BatchOperationResponse.FailResult> failList = new ArrayList<>();

        for (Long userId : userIds) {
            try {
                User user = userMapper.selectById(userId);
                if (user == null) {
                    failCount++;
                    failList.add(BatchOperationResponse.FailResult.builder()
                            .id(userId)
                            .reason("用户不存在")
                            .build());
                    continue;
                }

                // 不能禁用管理员自己
                if (userId.equals(UserContext.getUserId())) {
                    failCount++;
                    failList.add(BatchOperationResponse.FailResult.builder()
                            .id(userId)
                            .reason("不能禁用当前登录的管理员账号")
                            .build());
                    continue;
                }

                // 已经是禁用状态
                if (user.getStatus() == 0) {
                    failCount++;
                    failList.add(BatchOperationResponse.FailResult.builder()
                            .id(userId)
                            .reason("用户已是禁用状态")
                            .build());
                    continue;
                }

                user.setStatus(0);
                int result = userMapper.updateById(user);

                if (result > 0) {
                    successCount++;
                    log.info("禁用用户成功: userId={}, username={}", userId, user.getUserName());
                } else {
                    failCount++;
                    failList.add(BatchOperationResponse.FailResult.builder()
                            .id(userId)
                            .reason("更新失败")
                            .build());
                }
            } catch (Exception e) {
                failCount++;
                failList.add(BatchOperationResponse.FailResult.builder()
                        .id(userId)
                        .reason(e.getMessage())
                        .build());
                log.error("禁用用户失败: userId={}", userId, e);
            }
        }

        return BatchOperationResponse.builder()
                .totalCount(userIds.size())
                .successCount(successCount)
                .failCount(failCount)
                .failList(failList)
                .build();
    }

    /**
     * 批量启用用户
     */
    @Override
    @Transactional
    public BatchOperationResponse batchEnableUsers(List<Long> userIds) {
        int successCount = 0;
        int failCount = 0;
        List<BatchOperationResponse.FailResult> failList = new ArrayList<>();

        for (Long userId : userIds) {
            try {
                User user = userMapper.selectById(userId);
                if (user == null) {
                    failCount++;
                    failList.add(BatchOperationResponse.FailResult.builder()
                            .id(userId)
                            .reason("用户不存在")
                            .build());
                    continue;
                }

                // 已经是启用状态
                if (user.getStatus() == 1) {
                    failCount++;
                    failList.add(BatchOperationResponse.FailResult.builder()
                            .id(userId)
                            .reason("用户已是启用状态")
                            .build());
                    continue;
                }

                user.setStatus(1);
                int result = userMapper.updateById(user);

                if (result > 0) {
                    successCount++;
                    log.info("启用用户成功: userId={}, username={}", userId, user.getUserName());
                } else {
                    failCount++;
                    failList.add(BatchOperationResponse.FailResult.builder()
                            .id(userId)
                            .reason("更新失败")
                            .build());
                }
            } catch (Exception e) {
                failCount++;
                failList.add(BatchOperationResponse.FailResult.builder()
                        .id(userId)
                        .reason(e.getMessage())
                        .build());
                log.error("启用用户失败: userId={}", userId, e);
            }
        }

        return BatchOperationResponse.builder()
                .totalCount(userIds.size())
                .successCount(successCount)
                .failCount(failCount)
                .failList(failList)
                .build();
    }

    /**
     * 批量重置用户密码
     */
    @Override
    @Transactional
    public BatchOperationResponse batchResetPassword(List<Long> userIds) {
        String defaultPassword = "123456";
        String encryptedPassword = PasswordUtils.encrypt(defaultPassword);

        int successCount = 0;
        int failCount = 0;
        List<BatchOperationResponse.FailResult> failList = new ArrayList<>();

        for (Long userId : userIds) {
            try {
                User user = userMapper.selectById(userId);
                if (user == null) {
                    failCount++;
                    failList.add(BatchOperationResponse.FailResult.builder()
                            .id(userId)
                            .reason("用户不存在")
                            .build());
                    continue;
                }

                user.setPassword(encryptedPassword);
                int result = userMapper.updateById(user);

                if (result > 0) {
                    successCount++;
                    log.info("重置用户密码成功: userId={}, username={}", userId, user.getUserName());
                } else {
                    failCount++;
                    failList.add(BatchOperationResponse.FailResult.builder()
                            .id(userId)
                            .reason("更新失败")
                            .build());
                }
            } catch (Exception e) {
                failCount++;
                failList.add(BatchOperationResponse.FailResult.builder()
                        .id(userId)
                        .reason(e.getMessage())
                        .build());
                log.error("重置用户密码失败: userId={}", userId, e);
            }
        }

        return BatchOperationResponse.builder()
                .totalCount(userIds.size())
                .successCount(successCount)
                .failCount(failCount)
                .failList(failList)
                .build();
    }
}