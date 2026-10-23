package com.hongjie.pms.refactor.core.enums.biz;

import com.hongjie.pms.refactor.core.enums.CodableEnum;

/**
 * 系统公告状态，对应 {@code notice.status}：0-草稿 1-已发布 2-已下线。
 *
 * <p>注意这里 0 表示「草稿」，而 {@link UserStatus} 的 0 表示「禁用」——
 * 同一数字在不同限界上下文语义相反，这正是本方案不为状态建统一取值域的原因。
 */
public enum NoticeStatus implements CodableEnum<Integer> {

    DRAFT(0, "草稿"),
    PUBLISHED(1, "已发布"),
    OFFLINE(2, "已下线");

    private final Integer code;
    private final String desc;

    NoticeStatus(Integer code, String desc) {
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

    /** 是否对用户可见 */
    public boolean isVisibleToUser() {
        return this == PUBLISHED;
    }

    /** 是否允许编辑（已发布的公告不允许改动正文） */
    public boolean isEditable() {
        return this == DRAFT;
    }
}
