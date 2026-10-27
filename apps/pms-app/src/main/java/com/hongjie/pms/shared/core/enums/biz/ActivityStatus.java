package com.hongjie.pms.shared.core.enums.biz;

import com.hongjie.pms.shared.core.enums.CodableEnum;

/**
 * 活动状态，对应 {@code activity.status}：0-报名中 1-进行中 2-已结束 3-已下架。
 */
public enum ActivityStatus implements CodableEnum<Integer> {
    SIGNUP_OPEN(0, "报名中"),
    ONGOING(1, "进行中"),
    FINISHED(2, "已结束"),
    OFFLINE(3, "已下架");

    private final Integer code;
    private final String desc;

    ActivityStatus(Integer code, String desc) {
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

    /** 是否还接受报名 */
    public boolean acceptsSignup() {
        return this == SIGNUP_OPEN;
    }

    /** 是否已终态，不再需要状态机推进 */
    public boolean isTerminal() {
        return this == FINISHED || this == OFFLINE;
    }
}
