package com.hongjie.pms.shared.core.enums.biz;

import com.hongjie.pms.shared.core.enums.CodableEnum;

/**
 * 活动报名状态，对应 {@code activity_signup.status}：1-已报名 2-已取消 3-已签到 4-爽约。
 *
 * <p>注意「已取消」是 2 而非 0，历史代码里 {@code status == 4} 判定爽约散落在
 * {@code DelayTaskHandler} 等多处，v2 一律走本枚举。
 */
public enum SignupStatus implements CodableEnum<Integer> {
    SIGNED_UP(1, "已报名"),
    CANCELLED(2, "已取消"),
    CHECKED_IN(3, "已签到"),
    NO_SHOW(4, "爽约");

    private final Integer code;
    private final String desc;

    SignupStatus(Integer code, String desc) {
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

    /** 是否占用名额（已报名与已签到都算占位） */
    public boolean occupiesSeat() {
        return this == SIGNED_UP || this == CHECKED_IN;
    }

    /** 爽约会计入风控计数，触发禁报名惩罚 */
    public boolean isPunishable() {
        return this == NO_SHOW;
    }
}
