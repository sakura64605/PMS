package com.hongjie.pms.modules.identity.user.application;
import com.hongjie.pms.platform.utils.OssUtils;
import com.hongjie.pms.modules.identity.user.api.service.AvatarService;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.entity.AvatarHistory;
import com.hongjie.pms.modules.identity.user.api.dto.response.AvatarUploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@Service @Primary @RequiredArgsConstructor
public class PmsAvatarServiceImpl implements AvatarService {
    private final OssUtils oss;
    private final UserAvatarCommands commands;
    @Override public AvatarUploadResponse uploadAvatar(MultipartFile file) {
        Long actor = commands.actor();
        String url = oss.uploadAvatar(file, actor);
        commands.uploaded(actor, url);
        return AvatarUploadResponse.builder().avatarUrl(url).message("头像上传成功").build();
    }
    @Override public List<AvatarHistory> historyAvatar() { return commands.history(); }
    @Override public String switchToHistoryAvatar(Long id) { return commands.switchTo(id); }
}
