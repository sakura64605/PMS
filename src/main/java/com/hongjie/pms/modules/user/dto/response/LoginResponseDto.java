package com.hongjie.pms.modules.user.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponseDto {

    private String token;           // JWT令牌
    private String tokenPrefix;       // 令牌类型，通常是 "Bearer"
    private Long expiresIn;         // 过期时间（秒）

    // 用户基本信息
    private String username;
    private String nickname;
    private String avatar;
    private Integer role;

}
