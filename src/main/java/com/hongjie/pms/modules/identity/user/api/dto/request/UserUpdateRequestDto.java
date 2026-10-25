package com.hongjie.pms.modules.identity.user.api.dto.request;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.List;

@Data
@Builder
@lombok.extern.jackson.Jacksonized
public class UserUpdateRequestDto {

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 个性签名
     */
    private String signature;

    /**
     * 性别
     */
    private Integer gender;

    /**
     * 标签
     */
    private List<String> tags;

    /**
     * 隐私设置
     */
    private Map<String, Boolean> privacySettings;

    /**
     * 头像
     */
    private String avatar;

    /**
     * email
     */
    private String email;

    /**
     * 是否可搜索
     */
    private Integer searchable;

}
