package com.hongjie.pms.shared.core.enums.biz;

import com.hongjie.pms.shared.core.enums.CodableEnum;

/**
 * 公告类型，对应 {@code notice.type}：1-系统公告 2-活动通知 3-重要提醒。
 */
public enum NoticeType implements CodableEnum<Integer> {
    SYSTEM(1, "系统公告"),
    ACTIVITY(2, "活动通知"),
    IMPORTANT(3, "重要提醒");

    private final Integer code;
    private final String desc;

    NoticeType(Integer code, String desc) {
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
}
