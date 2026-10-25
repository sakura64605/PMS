package com.hongjie.pms.shared.core.enums.biz;

import com.hongjie.pms.shared.core.enums.CodableEnum;

/**
 * 评论状态，对应 {@code comment.status}：1-正常 0-已删除。
 *
 * <p>与多数表相反，这里「正常」是 1、「删除」是 0。
 */
public enum CommentStatus implements CodableEnum<Integer> {

    DELETED(0, "已删除"),
    NORMAL(1, "正常");

    private final Integer code;
    private final String desc;

    CommentStatus(Integer code, String desc) {
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

    public boolean isVisible() {
        return this == NORMAL;
    }
}
