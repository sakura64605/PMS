package com.hongjie.pms.shared.core.event.outbox;

import com.hongjie.pms.shared.core.enums.CodableEnum;

/**
 * 发件箱记录投递状态，对应 {@code pms_domain_event.status}：0-待投递 1-已投递 2-失败。
 */
public enum OutboxStatus implements CodableEnum<Integer> {

    PENDING(0, "待投递"),
    SENT(1, "已投递"),
    FAILED(2, "失败");

    private final Integer code;
    private final String desc;

    OutboxStatus(Integer code, String desc) {
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

    /** 是否还需要被兜底重投任务处理 */
    public boolean needsRetry() {
        return this == PENDING || this == FAILED;
    }
}
