package com.hongjie.pms.modules.identity.user.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequestDto {

    @NotBlank(message = "用户名或手机号不能为空")
    private String account;

    @NotBlank(message = "密码不能为空")
    private String password;
}
