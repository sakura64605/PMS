package com.hongjie.pms.modules.identity.user.api.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AvatarUploadResponse {

    private String avatarUrl; // 头像URL
    private String message; // 上传结果消息
}
