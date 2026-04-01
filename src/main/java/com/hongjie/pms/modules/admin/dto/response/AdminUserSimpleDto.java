package com.hongjie.pms.modules.admin.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminUserSimpleDto {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 是否禁用
     */
    private Boolean isDisable;

}
