package com.hongjie.pms.shared.core.enums.biz;

import com.hongjie.pms.shared.core.enums.CodableEnum;

/**
 * 宠物帖子类型，对应 {@code pet_post.type}：0-领养 1-救助。
 *
 * <p>这是与 {@link PetPostStatus} 正交的一条轴。注意 {@code audit_record.target_type}
 * 的历史方言 {@code adopt} / {@code help} 实际表达的就是这条轴，而非目标类型 —— 见
 * {@code TargetTypeCodec} 中关于「审计方言不可逆」的说明。
 */
public enum PetPostType implements CodableEnum<Integer> {

    ADOPT(0, "领养"),
    HELP(1, "救助");

    private final Integer code;
    private final String desc;

    PetPostType(Integer code, String desc) {
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
