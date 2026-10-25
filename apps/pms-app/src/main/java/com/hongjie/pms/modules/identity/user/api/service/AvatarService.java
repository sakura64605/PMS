package com.hongjie.pms.modules.identity.user.api.service;

import com.hongjie.pms.modules.identity.user.api.dto.response.AvatarUploadResponse;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.entity.AvatarHistory;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AvatarService {
    AvatarUploadResponse uploadAvatar(MultipartFile file);

    List<AvatarHistory> historyAvatar();

    String switchToHistoryAvatar(Long historyId);

//    void updateAvatar(String avatarUrl);
}
