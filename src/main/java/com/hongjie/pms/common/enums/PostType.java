package com.hongjie.pms.common.enums;

public enum PostType {
    HELP("help", "救助"),
    ADOPT("adopt", "领养"),
    ACTIVITY("activity", "活动");

    private final String code;
    private final String desc;

    PostType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static PostType fromCode(String code) {
        for (PostType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}