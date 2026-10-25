package com.hongjie.pms.modules.identity.user.api.dto.request;

import lombok.Data;

@Data
public class RegisterRequestDto {

    /**
     * 用户名
     */
    private String userName;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 密码
     */
    private String password;

    /**
     * 手机号
     */
    private String phone;

}
