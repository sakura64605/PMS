package com.hongjie.pms.shared.core.enums.biz;

import com.hongjie.pms.shared.core.enums.CodableEnum;

/**
 * 用户账号状态，对应 {@code user.status}：0-禁用 1-正常。
 */
public enum UserStatus implements CodableEnum<Integer> {

    DISABLED(0, "禁用"),
    NORMAL(1, "正常");

    private final Integer code;
    private final String desc;

    UserStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @Override
    public Integer getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }

    public boolean canLogin() {
        return this == NORMAL;
    }
}
