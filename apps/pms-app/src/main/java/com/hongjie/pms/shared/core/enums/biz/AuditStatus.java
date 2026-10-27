package com.hongjie.pms.shared.core.enums.biz;

import com.hongjie.pms.shared.core.enums.CodableEnum;

/**
 * 审核状态，对应 {@code pet_post.audit_status} / {@code activity.audit_status}
 * / {@code audit_record.audit_status}：0-待审核 1-审核通过 2-审核拒绝。
 *
 * <p>命名与老的 {@code com.hongjie.pms.platform.enums.AuditStatus} 相同但在不同包下：
 * 老类是 {@code common.enums} 包的历史实现，新代码只用本枚举。
 * 架构测试禁止老包引用 {@code refactor.core}，两者不会互相干扰。
 *
 * <p><b>与 {@link PetPostStatus} / {@link ActivityStatus} 的区别</b>：审核状态是一条独立轴。
 * 一篇帖子可以「审核通过」但业务上「已下架」，这两个字段必须分开推进。
 */
public enum AuditStatus implements CodableEnum<Integer> {
    PENDING(0, "待审核"),
    APPROVED(1, "审核通过"),
    REJECTED(2, "审核拒绝");

    private final Integer code;
    private final String desc;

    AuditStatus(Integer code, String desc) {
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

    public boolean isSettled() {
        return this != PENDING;
    }
}
