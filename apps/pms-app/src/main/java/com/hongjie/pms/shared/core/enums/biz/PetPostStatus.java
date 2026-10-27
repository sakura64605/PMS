package com.hongjie.pms.shared.core.enums.biz;

import com.hongjie.pms.shared.core.enums.CodableEnum;

/**
 * 宠物帖子状态，对应 {@code pet_post.status}。
 *
 * <p>取值来自建表注释：-1-已删除 0-待审核 1-已发布 2-已完成 3-已下架 4-已拒绝。
 * 老代码里全是 {@code setStatus(1)} / {@code getStatus() == 2} 这类魔法值，v2 一律走本枚举。
 */
public enum PetPostStatus implements CodableEnum<Integer> {
    DELETED(-1, "已删除"),
    PENDING_AUDIT(0, "待审核"),
    PUBLISHED(1, "已发布"),
    COMPLETED(2, "已完成"),
    OFFLINE(3, "已下架"),
    REJECTED(4, "已拒绝");

    private final Integer code;
    private final String desc;

    PetPostStatus(Integer code, String desc) {
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

    /** 是否对普通用户可见 */
    public boolean isVisibleToPublic() {
        return this == PUBLISHED || this == COMPLETED;
    }

    /** 是否处于终态（不可再被审核流程推进） */
    public boolean isTerminal() {
        return this == DELETED || this == REJECTED || this == OFFLINE;
    }
}
