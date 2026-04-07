package com.hongjie.pms.modules.user.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.utils.OssUtils;
import com.hongjie.pms.modules.user.dto.response.AvatarUploadResponse;
import com.hongjie.pms.modules.user.entity.AvatarHistory;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.AvatarHistoryMapper;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.modules.user.service.AvatarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AvatarServiceImpl implements AvatarService {

    private static final int MAX_HISTORY_SIZE = 11;  // 最多保留10个历史头像

    private final OssUtils ossUtil;
    private final UserMapper userMapper;
    private final AvatarHistoryMapper avatarHistoryMapper;

    @Override
    @Transactional
    public AvatarUploadResponse uploadAvatar(MultipartFile file) {
        Long userId = UserContext.getUserId();
        log.info("用户{}开始上传头像", userId);

        try {
            // 1. 获取当前用户信息
            User currentUser = userMapper.selectById(userId);
            String oldAvatar = currentUser.getAvatar();

            // 2. 上传新头像到OSS
            String newAvatarUrl = ossUtil.uploadAvatar(file, userId);
            log.info("新头像上传成功: {}", newAvatarUrl);

            AvatarHistory history = new AvatarHistory();
            history.setUserId(userId);
            history.setAvatarUrl(newAvatarUrl);
            avatarHistoryMapper.insert(history);
            log.info("将头像已保存到历史: {}", oldAvatar);

            // 4. 更新用户表的当前头像
            User updateUser = new User();
            updateUser.setId(userId);
            updateUser.setAvatar(newAvatarUrl);
            userMapper.updateById(updateUser);

            // 5. 清理多余的历史头像（保留最近10个）
            cleanOldHistory(userId);

            log.info("用户{}头像上传成功", userId);

            return AvatarUploadResponse.builder()
                    .avatarUrl(newAvatarUrl)
                    .message("头像上传成功")
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("上传失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.PARAM_ERROR, e.getMessage());
        } catch (Exception e) {
            log.error("上传失败", e);
            throw new BusinessException(ErrorCode.UPLOAD_FAIL);
        }
    }

    @Override
    public List<AvatarHistory> historyAvatar() {

        Long userId = UserContext.getUserId();

        List<AvatarHistory> historyList = avatarHistoryMapper.selectList(
                new QueryWrapper<AvatarHistory>()
                        .eq("user_id", userId)
                        .orderByDesc("update_time")  // 降序，最新的在前
        );

        log.info("用户{}历史头像数量: {}", userId, historyList.size());

        return historyList;
    }

    @Override
    @Transactional
    public String switchToHistoryAvatar(Long historyId) {
        Long userId = UserContext.getUserId();
        log.info("用户{}开始切换历史头像，历史头像ID: {}", userId, historyId);

        try {
            // 1. 获取历史头像信息
            AvatarHistory history = avatarHistoryMapper.selectById(historyId);
            if (history == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "历史头像不存在");
            }

            // 2. 验证历史头像是否属于当前用户
            if (!history.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }

            // 3. 更新用户表的当前头像
            User updateUser = new User();
            updateUser.setId(userId);
            updateUser.setAvatar(history.getAvatarUrl());
            userMapper.updateById(updateUser);

            // 4. 更新历史头像的updateTime（使其在历史列表中排在最前面）
            AvatarHistory updateHistory = new AvatarHistory();
            updateHistory.setId(historyId);
            updateHistory.setUpdateTime(LocalDateTime.now());
            avatarHistoryMapper.updateById(updateHistory);

            log.info("用户{}切换历史头像成功，新头像URL: {}", userId, history.getAvatarUrl());

            return history.getAvatarUrl();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("切换历史头像失败", e);
            throw new BusinessException(ErrorCode.PARAM_ERROR, "切换历史头像失败");
        }
    }

//    @Override
//    public void updateAvatar(String avatarUrl) {
//        Long userId = UserContext.getUserId();
//        log.info("用户{}开始更新头像", userId);
//
//        User user = new User();
//        user.setId(userId);
//        user.setAvatar(avatarUrl);
//        userMapper.updateById(user);
//    }

    /**
     * 清理超出限制的历史头像
     * 当历史头像超过10个时，删除最早的
     */
    private void cleanOldHistory(Long userId) {
        // 获取用户所有历史头像（按时间升序，最早的在前）
        List<AvatarHistory> historyList = avatarHistoryMapper.selectList(
                new QueryWrapper<AvatarHistory>()
                        .eq("user_id", userId)
                        .orderByAsc("update_time")  // 升序，最早的在前
        );

        int totalCount = historyList.size();
        log.info("用户{}当前历史头像数量: {}", userId, totalCount);

        // 如果超过限制，删除最早的
        if (totalCount > MAX_HISTORY_SIZE) {
            int deleteCount = totalCount - MAX_HISTORY_SIZE;

            // 取前 deleteCount 个（最早的）
            List<AvatarHistory> toDelete = historyList.subList(0, deleteCount);

            for (AvatarHistory history : toDelete) {
                try {
                    // 从OSS删除文件
                    ossUtil.deleteFile(history.getAvatarUrl());
                    // 删除数据库记录
                    avatarHistoryMapper.deleteById(history.getId());
                    log.info("自动清理用户{}的历史头像: {} (创建时间: {})",
                            userId, history.getAvatarUrl(), history.getUpdateTime());
                } catch (Exception e) {
                    log.error("清理历史头像失败: {}", history.getAvatarUrl(), e);
                }
            }

            log.info("用户{}历史头像清理完成，共删除{}个，当前保留{}个",
                    userId, deleteCount, MAX_HISTORY_SIZE);
        }
    }
}