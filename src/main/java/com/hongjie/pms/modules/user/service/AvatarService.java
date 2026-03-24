package com.hongjie.pms.modules.user.service;

import com.hongjie.pms.modules.user.dto.response.AvatarUploadResponse;
import com.hongjie.pms.modules.user.entity.AvatarHistory;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AvatarService {
    AvatarUploadResponse uploadAvatar(MultipartFile file);

    List<AvatarHistory> historyAvatar();

    String switchToHistoryAvatar(Long historyId);

//    void updateAvatar(String avatarUrl);
}
