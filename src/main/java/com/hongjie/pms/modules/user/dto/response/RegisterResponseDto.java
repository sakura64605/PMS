package com.hongjie.pms.modules.user.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RegisterResponseDto {

    /**
     * token
     */
    private String token;

    /**
     * token前缀
     */
    private String tokenPrefix;

    /**
     * 过期时间
     */
    private Long expiresIn;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 角色
     */
    private Integer role;

}
